# Transactional Outbox Pattern

This guide explains one of the most important patterns in distributed systems: the **Transactional Outbox Pattern**. It solves a fundamental problem when working with databases and message brokers.

## Table of Contents

1. [The Dual-Write Problem](#the-dual-write-problem)
2. [What is the Outbox Pattern?](#what-is-the-outbox-pattern)
3. [How Our Implementation Works](#how-our-implementation-works)
4. [Implementation Details](#implementation-details)
5. [Outbox Publisher](#outbox-publisher)
6. [Trade-offs](#trade-offs)
7. [Alternatives](#alternatives)

---

## The Dual-Write Problem

### The Scenario

You want to save payment to database AND publish event to Kafka **atomically**.

```java
@Service
public class PaymentService {
    public void processPayment(Payment payment) {
        paymentRepository.save(payment);           // Write to database
        kafkaTemplate.send("topic", paymentEvent);  // Send to Kafka

        // What if one succeeds but the other fails?
    }
}
```

### The Problems

**Problem 1: Database succeeds, Kafka fails**

```
1. Payment saved to database ✅
2. Kafka is down! ❌
Result: Payment exists but no event published
       → Ledger service never knows about payment
       → Data inconsistency!
```

**Problem 2: Kafka succeeds, database fails**

```
1. Event sent to Kafka ✅
2. Database constraint violation ❌
Result: Event published but payment doesn't exist
       → Ledger creates entry for non-existent payment
       → Data inconsistency!
```

**Problem 3: Partial failure**

```
1. Payment saved ✅
2. Kafka send appears to succeed ✅
3. Network error - unclear if Kafka actually received it ❓
4. Retry? → Duplicate event
   Don't retry? → Missing event
```

### Why This is Hard

**Different systems = No shared transaction**

- Database has transactions (BEGIN, COMMIT, ROLLBACK)
- Kafka doesn't participate in database transactions
- Can't make them atomic together!

**What doesn't work:**

❌ **Try-catch and rollback**

```java
@Transactional
public void processPayment(Payment payment) {
    try {
        paymentRepo.save(payment);
        kafkaTemplate.send("topic", event);  // Not part of transaction!
    } catch (Exception e) {
        // Too late - Kafka might have already received message
    }
}
```

❌ **Two-Phase Commit (2PC)**

- Requires Kafka to support it (it doesn't)
- Slow and blocking
- Single point of failure (coordinator)
- Avoid in distributed systems!

---

## What is the Outbox Pattern?

### The Core Idea

**Don't publish to Kafka directly!** Instead:

1. Save business data (payment) to database
2. Save event to **outbox table** in the **same database transaction**
3. Both commit atomically (ACID guarantees!)
4. **Separate process** reads outbox and publishes to Kafka
5. Mark events as published

### Visual Flow

```
┌─────────────────────────────────────────────┐
│         PaymentService (Transaction)        │
│  ┌──────────────┐      ┌──────────────┐    │
│  │   payments   │      │outbox_events │    │
│  │    table     │      │    table     │    │
│  └──────────────┘      └──────────────┘    │
│         ↓                      ↓            │
│    Save payment           Save event        │
│                                             │
│    Both commit together! ✅                 │
└─────────────────────────────────────────────┘
                    ↓
            Database contains:
            - Payment record
            - Outbox event (unpublished)
                    ↓
┌─────────────────────────────────────────────┐
│        OutboxPublisher (@Scheduled)         │
│  1. SELECT * FROM outbox_events             │
│     WHERE published = false                 │
│  2. FOR EACH event:                         │
│     - Publish to Kafka                      │
│     - UPDATE outbox_events                  │
│       SET published = true                  │
└─────────────────────────────────────────────┘
```

### Key Insight

By putting the event in the **same database** as the payment:

- Both writes happen in the same transaction
- ACID properties guarantee atomicity
- Event will eventually reach Kafka (eventual consistency)
- No data loss even if Kafka is down

---

## How Our Implementation Works

### Step 1: Service Layer (PaymentService)

```java
@Service
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @Transactional  // Single database transaction!
    public PaymentResponse initiatePayment(PaymentRequest request) {
        // 1. Create and save payment
        Payment payment = new Payment(...);
        payment = paymentRepo.save(payment);

        // 2. Create event DTO
        PaymentEvent event = new PaymentEvent(
            payment.getId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            PaymentEventType.PAYMENT_INITIATED
        );

        // 3. Serialize to JSON
        String eventPayload = objectMapper.writeValueAsString(event);

        // 4. Save to outbox (SAME TRANSACTION!)
        OutboxEvent outboxEvent = new OutboxEvent(
            "Payment",                    // aggregateType
            payment.getId(),              // aggregateId
            "PAYMENT_INITIATED",          // eventType
            eventPayload                  // payload (JSON)
        );
        outboxRepo.save(outboxEvent);

        // Transaction commits here
        // Both payment AND outbox event are now in database!

        return response;
    }
}
```

**What happens:**

```
BEGIN TRANSACTION
  INSERT INTO payments (...);
  INSERT INTO outbox_events (...);
COMMIT

Result: Both rows inserted atomically ✅
```

### Step 2: Outbox Table (OutboxEvent Entity)

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;  // "Payment"
    private Long aggregateId;      // paymentId
    private String eventType;      // "PAYMENT_INITIATED"
    private String payload;        // JSON event data
    private Boolean published;     // false → not yet sent to Kafka
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
```

**Database after payment:**

```sql
SELECT * FROM payments WHERE id = 123;
-- id=123, userId=1, amount=100.00, ...

SELECT * FROM outbox_events WHERE aggregate_id = 123;
-- id=1, aggregateType="Payment", aggregateId=123,
-- eventType="PAYMENT_INITIATED", published=false
```

### Step 3: Outbox Publisher (OutboxPublisher)

```java
@Service
public class OutboxPublisher {
    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Run every 5 seconds
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        // Find unpublished events
        List<OutboxEvent> pending = outboxRepo.findByPublishedFalse();

        for (OutboxEvent event : pending) {
            try {
                // Publish to Kafka
                kafkaTemplate.send("payment-events", event.getPayload());

                // Mark as published
                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepo.save(event);

            } catch (Exception e) {
                // Log error, event remains unpublished
                // Will retry on next run
            }
        }
    }
}
```

**Execution timeline:**

```
T=0s:  Payment created, outbox event saved (published=false)
T=5s:  OutboxPublisher runs
       → Finds event
       → Publishes to Kafka ✅
       → Marks published=true
T=10s: OutboxPublisher runs
       → No unpublished events
       → Nothing to do
```

---

## Implementation Details

### Outbox Repository

```java
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    // Find events that haven't been published yet
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();

    // Count pending events (for monitoring)
    long countByPublishedFalse();
}
```

### Publishing Logic

```java
private void publishEvent(OutboxEvent outboxEvent) {
    // Parse JSON payload
    PaymentEvent event = objectMapper.readValue(
        outboxEvent.getPayload(),
        PaymentEvent.class
    );

    // Publish to Kafka
    // Key = paymentId (for partitioning)
    // Value = event JSON
    kafkaTemplate.send(
        "payment-events",
        event.getPaymentId().toString(),  // Key
        outboxEvent.getPayload()          // Value
    );
}
```

### Error Handling

```java
for (OutboxEvent event : pendingEvents) {
    try {
        publishEvent(event);
        event.markAsPublished();
        outboxRepo.save(event);
    } catch (Exception e) {
        logger.error("Failed to publish event {}: {}",
                    event.getId(), e.getMessage());
        // Event remains unpublished
        // Will be retried on next scheduler run
    }
}
```

**If Kafka is down:**

```
T=0s:   Payment created, outbox event saved
T=5s:   Publish fails (Kafka down) ❌
        Event still published=false
T=10s:  Publish fails again ❌
        Event still published=false
T=15s:  Kafka is back up!
        Publish succeeds ✅
        Event marked published=true
```

---

## Outbox Publisher

### Polling Strategy

We use `@Scheduled` to poll the database periodically:

```java
@Scheduled(fixedDelay = 5000, initialDelay = 10000)
```

**Parameters:**

- `fixedDelay = 5000`: Wait 5 seconds after previous run completes
- `initialDelay = 10000`: Wait 10 seconds after application startup

**Tuning considerations:**

| Interval   | Pros                      | Cons               |
| ---------- | ------------------------- | ------------------ |
| 1 second   | Low latency               | High database load |
| 5 seconds  | **Balanced** (our choice) | Slight delay       |
| 30 seconds | Low overhead              | Higher latency     |

### Alternative: Change Data Capture (CDC)

Instead of polling, monitor the database transaction log:

```
PostgreSQL WAL (Write-Ahead Log)
    ↓
Debezium CDC connector
    ↓
Kafka Connect
    ↓
Kafka Topic

No polling! Near-zero latency!
```

**CDC Benefits:**
✅ No polling overhead  
✅ Near-instant event publishing  
✅ Guaranteed ordering  
✅ Captures ALL changes

**CDC Drawbacks:**
❌ More complex setup  
❌ Requires Debezium/Kafka Connect  
❌ Database-specific (PostgreSQL, MySQL, etc.)

**For Stage 2:** We use polling (simpler)  
**For Production:** Consider CDC with Debezium

---

## Trade-offs

### Pros ✅

1. **Guaranteed Delivery**

   - Events will eventually reach Kafka
   - No data loss even if Kafka is down

2. **Simplicity**

   - Uses standard database transactions
   - No distributed transaction protocols (2PC)
   - Easy to understand and debug

3. **Performance**

   - Database writes are fast (one transaction)
   - Kafka publishing is async (doesn't block business logic)

4. **Resilience**

   - Kafka downtime doesn't affect payment processing
   - Events accumulate and publish when Kafka recovers

5. **Audit Trail**
   - Outbox table shows what was published when
   - Can troubleshoot event delivery

### Cons ❌

1. **Eventual Consistency**

   - Slight delay between database write and event publish
   - Typically 5 seconds (polling interval)

2. **Extra Database Table**

   - Storage overhead for outbox events
   - Need cleanup/archiving strategy

3. **Polling Overhead**

   - Queries database every N seconds
   - Can be eliminated with CDC

4. **Complexity**
   - More code than direct Kafka publish
   - Need to manage outbox lifecycle

---

## Alternatives

### 1. Transaction Log Tailing (CDC)

**How it works:**

- Monitor database transaction log
- Detect new outbox rows
- Publish to Kafka immediately

**Tools:**

- Debezium
- Maxwell's Daemon
- AWS DMS

**When to use:**

- Need lower latency (<1 second)
- High event volume
- Production systems

### 2. Two-Phase Commit (2PC)

**How it works:**

- Coordinator asks participants "ready to commit?"
- If all say yes, coordinator says "commit"
- If any say no, coordinator says "rollback"

**Problems:**

- Blocking protocol (slow)
- Single point of failure (coordinator)
- Kafka doesn't support 2PC
- **Avoid in distributed systems!**

### 3. Saga Pattern

**How it works:**

- Break operation into local transactions
- Each service does its part
- Compensating actions for rollback

**Difference from Outbox:**

- Outbox: Ensures DB + Events atomic
- Saga: Manages multi-service transactions

**They work together!**

- Outbox ensures each service publishes reliably
- Saga coordinates multiple services

→ See [saga-pattern.md](saga-pattern.md)

---

## Summary

**The Problem:**

- Can't make database writes and Kafka publishing atomic
- Leads to data inconsistency

**The Solution: Transactional Outbox**

1. Save business data + event in **same database transaction**
2. ACID guarantees both commit together
3. Separate process publishes events to Kafka
4. Eventual consistency, guaranteed delivery

**Our Implementation:**

- `PaymentService`: Saves payment + outbox event (@Transactional)
- `OutboxEvent`: Database table for pending events
- `OutboxPublisher`: @Scheduled polling + Kafka publishing
- Retry logic ensures eventual delivery

**Key Takeaways:**
✅ Use outbox pattern when mixing database and messaging  
✅ Accept eventual consistency (5-second delay)  
✅ Always mark events as published after sending  
✅ Monitor outbox lag in production  
✅ Consider CDC for lower latency

**Next Steps:**

- See it in action: Run docker-compose and create a payment
- Check outbox table: `SELECT * FROM outbox_events;`
- View Kafka messages: Kafka UI at http://localhost:8090
- Read [saga-pattern.md](saga-pattern.md) for distributed transactions

The Transactional Outbox pattern is your friend in distributed systems! 🎯
