# Saga Pattern - Distributed Transactions

This guide explains the **Saga Pattern**, a crucial pattern for managing distributed transactions across multiple microservices without using distributed locks or two-phase commit.

## Table of Contents

1. [The Distributed Transaction Problem](#the-distributed-transaction-problem)
2. [What is a Saga?](#what-is-a-saga)
3. [Choreography vs Orchestration](#choreography-vs-orchestration)
4. [Our Implementation (Choreography)](#our-implementation-choreography)
5. [Compensating Transactions](#compensating-transactions)
6. [Failure Scenarios](#failure-scenarios)
7. [Best Practices](#best-practices)
8. [When to Use Sagas](#when-to-use-sagas)

---

## The Distributed Transaction Problem

### The Scenario

A payment involves multiple services:

1. **Payment Service**: Creates payment record
2. **Ledger Service**: Records transaction
3. **Account Service**: Updates balance
4. **Notification Service**: Sends confirmation email

**Question**: How do we ensure all steps complete or none of them do?

### Why @Transactional Doesn't Work

```java
// This WON'T work across services!
@Transactional
public void processPayment(PaymentRequest request) {
    paymentService.createPayment(request);      // Service 1
    ledgerService.recordTransaction(request);    // Service 2
    accountService.updateBalance(request);       // Service 3
    notificationService.sendEmail(request);      // Service 4
}
```

**Problem**: `@Transactional` only works within a single database!

- Each service has its own database
- Can't span transactions across services
- No shared transaction manager

### What Doesn't Work

❌ **Distributed Transactions (2PC - Two-Phase Commit)**

```
Problems:
- Blocking protocol (slow)
- Single point of failure (coordinator)
- Locks across services (deadlocks possible)
- Not suitable for microservices!
```

❌ **Hoping for the best**

```java
createPayment();        // Succeeds ✅
recordTransaction();    // Succeeds ✅
updateBalance();        // Fails ❌
sendEmail();           // Never runs

Result: Payment created but balance not updated!
        Data inconsistency across services.
```

---

## What is a Saga?

A **Saga** is a sequence of local transactions where:

1. Each service performs its local transaction
2. Publishes an event upon completion
3. Next service listens for event and does its work
4. If any step fails, **compensating transactions** undo previous steps

### Saga vs Regular Transaction

**Regular Transaction (Single Database):**

```
BEGIN
  INSERT into payments ...
  INSERT into ledger ...
  UPDATE accounts ...
COMMIT (or ROLLBACK if error)
```

**Saga (Multiple Services):**

```
Payment Service:
  BEGIN
    INSERT into payments ...
    INSERT into outbox (PaymentCreated event)
  COMMIT

  → Event published to Kafka

Ledger Service:
  BEGIN
    INSERT into ledger ...
    INSERT into outbox (LedgerRecorded event)
  COMMIT

  → Event published to Kafka

Account Service:
  BEGIN
    UPDATE accounts ...
  COMMIT
```

**Key difference:**

- Regular: All-or-nothing in single COMMIT
- Saga: Series of independent COMMITs + eventual consistency

---

## Choreography vs Orchestration

### Choreography (Decentralized)

**How it works:**

- Each service knows what to do when it sees an event
- No central coordinator
- Services react to events independently

**Example flow:**

```
Payment Service → PAYMENT_INITIATED event
                       ↓
Ledger Service (listening) → Creates ledger entry
                           → LEDGER_RECORDED event
                                    ↓
Payment Service (listening) → Updates status to COMPLETED
```

**Implementation:**

```java
// Payment Service
@Transactional
public void initiatePayment() {
    // 1. Create payment
    payment = paymentRepo.save(payment);

    // 2. Publish event
    publishEvent(PAYMENT_INITIATED);
}

// Ledger Service
@KafkaListener(topics = "payment-events")
public void onPaymentInitiated(PaymentEvent event) {
    if (event.getType() == PAYMENT_INITIATED) {
        // 1. Create ledger entry
        ledgerRepo.save(entry);

        // 2. Publish event
        publishEvent(LEDGER_RECORDED);
    }
}

// Payment Service
@KafkaListener(topics = "ledger-events")
public void onLedgerRecorded(LedgerEvent event) {
    // Update payment status
    payment.setStatus(COMPLETED);
    paymentRepo.save(payment);
}
```

**Pros:**
✅ Simple - no coordinator  
✅ Loosely coupled  
✅ Easy to add new services  
✅ Each service is independent

**Cons:**
❌ Hard to track overall flow  
❌ Difficult to debug  
❌ Can't easily see "big picture"  
❌ Circular dependencies possible

### Orchestration (Centralized)

**How it works:**

- Central orchestrator tells each service what to do
- Services don't know about each other
- Orchestrator manages the workflow

**Example flow:**

```
Orchestrator:
  1. Call Payment Service → Create payment
  2. If success:
     Call Ledger Service → Record transaction
  3. If success:
     Call Account Service → Update balance
  4. If success:
     Call Notification Service → Send email
  5. If any fails:
     Call compensation functions
```

**Implementation:**

```java
@Service
public class PaymentOrchestrator {
    public void processPayment(PaymentRequest request) {
        Long paymentId = null;
        Long ledgerId = null;

        try {
            // Step 1: Create payment
            paymentId = paymentService.createPayment(request);

            // Step 2: Record in ledger
            ledgerId = ledgerService.recordTransaction(paymentId);

            // Step 3: Update account
            accountService.updateBalance(request);

            // Step 4: Send notification
            notificationService.sendEmail(request);

        } catch (Exception e) {
            // Compensate (undo previous steps)
            if (ledgerId != null) {
                ledgerService.cancelTransaction(ledgerId);
            }
            if (paymentId != null) {
                paymentService.cancelPayment(paymentId);
            }
            throw e;
        }
    }
}
```

**Pros:**
✅ Clear workflow  
✅ Easy to understand  
✅ Centralized error handling  
✅ Can track state

**Cons:**
❌ Single point of failure (orchestrator)  
❌ Tight coupling to orchestrator  
❌ Orchestrator becomes complex  
❌ Extra network hop

### Which to Choose?

| Scenario                       | Use                                   |
| ------------------------------ | ------------------------------------- |
| Simple workflow (2-3 services) | **Choreography**                      |
| Complex workflow (5+ services) | **Orchestration**                     |
| Services frequently change     | **Choreography**                      |
| Need audit trail of workflow   | **Orchestration**                     |
| New team (learning)            | **Choreography** (simpler)            |
| Production critical            | **Orchestration** (better visibility) |

**Our project**: Uses **choreography** (simple, educational)

---

## Our Implementation (Choreography)

### The Flow

```
┌─────────────────────────────────────────────────────────┐
│                    Payment Saga                         │
└─────────────────────────────────────────────────────────┘

1. User creates payment via API
        ↓
┌──────────────────────────┐
│   Payment Service        │
│  ┌────────────────────┐  │
│  │ Create Payment     │  │
│  │ Status: PENDING    │  │
│  └────────────────────┘  │
│           ↓              │
│  ┌────────────────────┐  │
│  │ Save to Outbox     │  │
│  │ PAYMENT_INITIATED  │  │
│  └────────────────────┘  │
└──────────────────────────┘
            ↓
       Kafka Topic
     "payment-events"
            ↓
┌──────────────────────────┐
│   Ledger Service         │
│  ┌────────────────────┐  │
│  │ Consume Event      │  │
│  └────────────────────┘  │
│           ↓              │
│  ┌────────────────────┐  │
│  │ Create Ledger Entry│  │
│  └────────────────────┘  │
│           ↓              │
│  ┌────────────────────┐  │
│  │ Publish Event      │  │
│  │ LEDGER_RECORDED    │  │
│  └────────────────────┘  │
└──────────────────────────┘
            ↓
       Kafka Topic
     "ledger-events"
            ↓
┌──────────────────────────┐
│   Payment Service        │
│  ┌────────────────────┐  │
│  │ Consume Event      │  │
│  └────────────────────┘  │
│           ↓              │
│  ┌────────────────────┐  │
│  │ Update Payment     │  │
│  │ Status: COMPLETED  │  │
│  └────────────────────┘  │
└──────────────────────────┘

✅ Saga Complete!
```

### Code Implementation

**Payment Service - Initiate:**

```java
@Service
public class PaymentService {
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        // Create payment
        Payment payment = new Payment(...);
        payment.setStatus(PENDING);
        payment = paymentRepo.save(payment);

        // Create outbox event (same transaction!)
        OutboxEvent event = new OutboxEvent(
            "Payment",
            payment.getId(),
            "PAYMENT_INITIATED",
            toJson(new PaymentEvent(payment))
        );
        outboxRepo.save(event);

        return toResponse(payment);
    }
}
```

**Ledger Service - React:**

```java
@Component
public class PaymentEventListener {
    @KafkaListener(topics = "payment-events")
    @Transactional
    public void handlePaymentEvent(String eventJson) {
        PaymentEvent event = fromJson(eventJson);

        if (event.getType() == PAYMENT_INITIATED) {
            // Create ledger entry
            LedgerEntry entry = new LedgerEntry(event);
            ledgerRepo.save(entry);

            // Publish success event
            publishEvent(new LedgerEvent(LEDGER_RECORDED));
        }
    }
}
```

**Payment Service - Complete:**

```java
@Component
public class LedgerEventListener {
    @KafkaListener(topics = "ledger-events")
    @Transactional
    public void handleLedgerEvent(String eventJson) {
        LedgerEvent event = fromJson(eventJson);

        if (event.getType() == LEDGER_RECORDED) {
            // Update payment status
            Payment payment = paymentRepo.findById(event.getPaymentId());
            payment.setStatus(COMPLETED);
            paymentRepo.save(payment);
        }
    }
}
```

---

## Compensating Transactions

### What are Compensating Transactions?

**Compensating transactions** are operations that undo the effects of previous steps when a saga fails.

Think of them as the "Ctrl+Z" of distributed transactions.

### Example: Payment Fails After Ledger Created

```
1. Payment created (PENDING) ✅
2. Ledger entry created ✅
3. Account update fails ❌

   Now what? Can't rollback across services!

Solution: Run compensating transactions
4. Mark ledger as CANCELLED
5. Mark payment as FAILED
```

### Implementation

**Payment Service:**

```java
@KafkaListener(topics = "account-events")
@Transactional
public void handleAccountEvent(String eventJson) {
    AccountEvent event = fromJson(eventJson);

    if (event.getType() == ACCOUNT_UPDATE_FAILED) {
        // Compensate: Mark payment as failed
        Payment payment = paymentRepo.findById(event.getPaymentId());
        payment.setStatus(FAILED);
        paymentRepo.save(payment);

        // Publish compensation event
        publishEvent(new PaymentEvent(PAYMENT_FAILED));
    }
}
```

**Ledger Service:**

```java
@KafkaListener(topics = "payment-events")
@Transactional
public void handlePaymentEvent(String eventJson) {
    PaymentEvent event = fromJson(eventJson);

    if (event.getType() == PAYMENT_FAILED) {
        // Compensate: Cancel ledger entry
        LedgerEntry entry = ledgerRepo.findByPaymentId(event.getPaymentId());
        entry.setStatus(CANCELLED);
        ledgerRepo.save(entry);
    }
}
```

### Compensation Strategies

**1. Logical Delete**

```java
// Don't actually delete, just mark as cancelled
entry.setStatus(CANCELLED);
```

**2. Reversal Entry**

```java
// Create opposing entry (accounting style)
LedgerEntry reversal = new LedgerEntry();
reversal.setAmount(originalEntry.getAmount().negate());
reversal.setType(REVERSAL);
```

**3. State Machine**

```java
// Track saga state
PENDING → PROCESSING → COMPLETED
       ↓
    COMPENSATING → CANCELLED
```

---

## Failure Scenarios

### Scenario 1: Service Down During Saga

**Problem:**

```
1. Payment created ✅
2. Ledger service is down ❌
   Event sits in Kafka
```

**Solution:**

- Event remains in Kafka queue
- Ledger service will consume when it comes back up
- **Eventual consistency** - saga completes eventually

### Scenario 2: Duplicate Events

**Problem:**

```
1. Ledger service processes event ✅
2. Ledger service crashes before committing offset ❌
3. Kafka redelivers event
4. Duplicate ledger entry! ❌
```

**Solution: Idempotency**

```java
@KafkaListener(topics = "payment-events")
public void handleEvent(PaymentEvent event, @Header(OFFSET) long offset) {
    String messageId = "payment-events-" + offset;

    // Check if already processed
    if (ledgerRepo.existsByMessageId(messageId)) {
        return; // Skip duplicate
    }

    // Process and save with messageId
    LedgerEntry entry = new LedgerEntry(event);
    entry.setMessageId(messageId);
    ledgerRepo.save(entry);
}
```

### Scenario 3: Partial Failure

**Problem:**

```
1. Payment created ✅
2. Ledger created ✅
3. Account update fails ❌
```

**Solution: Compensating Transactions**

```
4. Publish ACCOUNT_UPDATE_FAILED event
5. Ledger service compensates (marks CANCELLED)
6. Payment service compensates (marks FAILED)
```

---

## Best Practices

### 1. Design for Idempotency

```java
// Always use message IDs
@KafkaListener
public void handle(@Payload String event,
                  @Header(OFFSET) long offset) {
    String messageId = createMessageId(offset);
    if (alreadyProcessed(messageId)) return;

    process(event);
    saveMessageId(messageId);
}
```

### 2. Use Semantic Locks

```java
// Prevent concurrent sagas on same entity
@Entity
public class Payment {
    @Version
    private Long version;  // Optimistic locking

    private String sagaId;  // Track which saga owns this
}
```

### 3. Timeout Sagas

```java
// Don't let sagas run forever
@Scheduled(fixedDelay = 60000)
public void timeoutStaleSagas() {
    List<Payment> stale = paymentRepo.findByStatusAndCreatedAtBefore(
        PENDING,
        LocalDateTime.now().minus(5, MINUTES)
    );

    for (Payment payment : stale) {
        compensate(payment);
    }
}
```

### 4. Monitor Saga Health

Track:

- Saga completion rate
- Average completion time
- Compensation rate
- Stuck sagas

### 5. Document Compensations

```java
/**
 * Compensating transaction for payment failure
 *
 * Steps:
 * 1. Mark payment as FAILED
 * 2. Publish PAYMENT_FAILED event
 * 3. Ledger service will cancel entry
 * 4. Account service will reverse reservation
 */

```

---

## When to Use Sagas

### Use Sagas When:

✅ Multiple services need to coordinate  
✅ Each service has its own database  
✅ Can tolerate eventual consistency  
✅ Need to avoid distributed locks  
✅ Compensation is possible

### Don't Use Sagas When:

❌ Single service (use @Transactional)  
❌ Need immediate consistency  
❌ Compensation is impossible/unsafe  
❌ Workflow is super simple (2 steps)

### Saga vs Alternative

| Scenario                   | Solution                      |
| -------------------------- | ----------------------------- |
| Single database            | **@Transactional**            |
| Two services, simple       | **Transactional Outbox** only |
| Multiple services, complex | **Saga (Orchestration)**      |
| Multiple services, simple  | **Saga (Choreography)**       |
| Financial transaction      | **Saga + Idempotency**        |

---

## Summary

**The Problem:**

- Can't use @Transactional across services
- Need coordinated actions across multiple databases
- Must handle partial failures

**The Solution: Saga Pattern**

- Sequence of local transactions
- Each publishes events upon completion
- Compensating transactions for rollback
- Eventual consistency

**Two Styles:**

1. **Choreography**: Services react to events (decentralized)
2. **Orchestration**: Central coordinator (centralized)

**Key Concepts:**

- **Local transactions**: Each service uses its own @Transactional
- **Events**: Communicate progress via Kafka
- **Compensations**: Undo previous steps on failure
- **Idempotency**: Handle duplicate events safely
- **Eventual consistency**: Accept slight delays

**Our Implementation:**

- Uses **choreography** (simpler for learning)
- Combines with **Transactional Outbox** for reliability
- Demonstrates **compensating transactions**

**Remember:**
✅ Design compensations from the start  
✅ Make everything idempotent  
✅ Monitor saga health  
✅ Timeout stuck sagas  
✅ Document the flow

**Next Steps:**

- Run docker-compose and test the saga flow
- Simulate failures (stop services mid-saga)
- Check database state during saga execution
- Read [kafka-intro.md](kafka-intro.md) for Kafka basics
- Read [outbox-pattern.md](outbox-pattern.md) for reliable messaging

Sagas give you distributed transactions without distributed disasters! 🎭
