# Transactional Basics - Understanding Database Transactions

Transactions are CRITICAL for financial applications. This guide explains how Spring's `@Transactional` annotation ensures data consistency.

## Table of Contents

1. [What is a Transaction?](#what-is-a-transaction)
2. [ACID Properties](#acid-properties)
3. [@Transactional in Spring](#transactional-in-spring)
4. [Transaction Boundaries](#transaction-boundaries)
5. [Rollback Behavior](#rollback-behavior)
6. [Real-World Example](#real-world-example)

---

## What is a Transaction?

A **database transaction** is a unit of work that either completely succeeds or completely fails - there's no in-between.

**Think of it like a bank transfer:**

```
Transfer $100 from Alice to Bob:
1. Deduct $100 from Alice's account    ← Must happen
2. Add $100 to Bob's account           ← Must happen

Both steps must succeed, or neither should happen!
```

**Without transactions:**

```
1. Deduct $100 from Alice ✅
2. Server crashes! 💥
3. Bob never gets the money ❌

Result: $100 disappeared! 😱
```

**With transactions:**

```
Transaction Start
├─ 1. Deduct $100 from Alice
├─ 2. Server crashes! 💥
└─ Transaction ROLLBACK

Result: Alice still has her $100 ✅
```

---

## ACID Properties

ACID is an acronym for four properties that guarantee transaction reliability:

### A - Atomicity

**All or Nothing** - a transaction is indivisible.

```java
@Transactional
public void transferMoney(Long fromAccount, Long toAccount, BigDecimal amount) {
    accountRepo.debit(fromAccount, amount);      // Step 1
    accountRepo.credit(toAccount, amount);       // Step 2

    // If ANY step fails, ALL changes are rolled back
}
```

### C - Consistency

**Valid State to Valid State** - database rules are never violated.

```java
// Rule: Balance cannot be negative
@Transactional
public void withdraw(Long accountId, BigDecimal amount) {
    Account account = accountRepo.findById(accountId);

    if (account.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException();  // Rollback!
    }

    account.setBalance(account.getBalance().subtract(amount));
    accountRepo.save(account);

    // Account balance is never negative ✅
}
```

### I - Isolation

**Concurrent transactions don't interfere** - multiple users can operate simultaneously without corrupting data.

```java
// Two users trying to withdraw from the same account simultaneously
// Isolation ensures they don't both succeed if there's not enough money

User 1: withdraw(account1, $50)
User 2: withdraw(account1, $50)
Account balance: $80

// Without isolation: Both might see $80 and succeed → balance = -$20 ❌
// With isolation: One succeeds, one fails → balance = $30 ✅
```

### D - Durability

**Once committed, changes are permanent** - even if the server crashes immediately after, the data is safe.

```java
@Transactional
public void saveUser(User user) {
    userRepo.save(user);
    // When method completes successfully, changes are COMMITTED to disk
}
// Server can crash here, user is still saved ✅
```

---

## @Transactional in Spring

### Basic Usage

```java
@Service
public class AccountService {

    private final AccountRepository accountRepo;

    @Transactional  // ← This method runs in a transaction
    public void convert(Long userId, Currency from, Currency to, BigDecimal amount) {
        Account account = accountRepo.findById(userId).orElseThrow();

        account.debit(from, amount);
        account.credit(to, amount);

        accountRepo.save(account);

        // If we reach here, changes are COMMITTED
        // If exception is thrown, changes are ROLLED BACK
    }
}
```

### How It Works (Behind the Scenes)

```java
// What you write:
@Transactional
public void myMethod() {
    // Your code
}

// What Spring actually does:
public void myMethod() {
    try {
        transactionManager.begin();        // Start transaction

        // Your code

        transactionManager.commit();       // Save changes to database
    } catch (Exception e) {
        transactionManager.rollback();     // Undo all changes
        throw e;
    }
}
```

### Read-Only Transactions

```java
@Transactional(readOnly = true)
public User findUser(Long id) {
    return userRepo.findById(id).orElseThrow();
}
```

Benefits of `readOnly = true`:

- Performance optimization (database can use read-only optimizations)
- Prevents accidental writes
- Clearer intent

---

## Transaction Boundaries

### Class-Level vs Method-Level

```java
@Service
@Transactional  // All public methods are transactional by default
public class UserService {

    public void method1() {
        // Transactional
    }

    public void method2() {
        // Transactional
    }

    @Transactional(readOnly = true)  // Override class-level
    public void method3() {
        // Read-only transactional
    }
}
```

### Propagation

What happens when a transactional method calls another transactional method?

```java
@Service
public class OrderService {

    @Autowired
    private PaymentService paymentService;

    @Transactional
    public void placeOrder(Order order) {
        orderRepo.save(order);              // Part of transaction
        paymentService.processPayment(order);  // Uses same transaction (default)
    }
}

@Service
public class PaymentService {

    @Transactional  // By default, joins existing transaction
    public void processPayment(Order order) {
        paymentRepo.save(new Payment(order));
    }
}
```

**Propagation options:**

- `REQUIRED` (default) - Join existing transaction or create new one
- `REQUIRES_NEW` - Always create a new transaction (suspend existing)
- `NESTED` - Create a savepoint within existing transaction
- `NEVER` - Throw exception if called within a transaction

---

## Rollback Behavior

### What Triggers Rollback?

**By default:**

- ✅ Unchecked exceptions (`RuntimeException` and subclasses) → ROLLBACK
- ❌ Checked exceptions (`Exception` and subclasses) → NO rollback

```java
@Transactional
public void example1() {
    userRepo.save(new User());
    throw new RuntimeException("Error!");  // ROLLBACK - user not saved
}

@Transactional
public void example2() throws IOException {
    userRepo.save(new User());
    throw new IOException("Error!");  // NO ROLLBACK - user IS saved!
}
```

### Custom Rollback Rules

```java
// Rollback on any exception
@Transactional(rollbackFor = Exception.class)
public void method() throws Exception {
    // ...
}

// Don't rollback on specific exception
@Transactional(noRollbackFor = MinorException.class)
public void method() {
    // ...
}
```

### Manual Rollback

```java
@Transactional
public void method() {
    try {
        // Some operation
    } catch (BusinessException e) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        // Handle exception without throwing
    }
}
```

---

## Real-World Example

Here's the currency conversion from our account-service:

```java
@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final ExchangeClient exchangeClient;

    @Transactional
    public ConvertResponse convert(Long userId, Currency from, Currency to, BigDecimal amount) {
        // 1. Lock and fetch account (prevents concurrent modifications)
        Account account = accountRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // 2. Validate sufficient balance
        if (account.getBalance(from).compareTo(amount) < 0) {
            throw new InsufficientFundsException("Not enough money");
            // Exception thrown → Transaction ROLLBACK
        }

        // 3. Get quote from exchange
        QuoteResponse quote = exchangeClient.getQuote(new QuoteRequest(from, to, amount));

        // 4. Execute trade
        ExecuteTradeResponse tradeResult = exchangeClient.executeTrade(
                new ExecuteTradeRequest(quote.getQuoteId()));

        if (!tradeResult.isSuccess()) {
            throw new ExchangeUnavailableException("Trade failed");
            // Exception thrown → Transaction ROLLBACK
            // Account balances remain unchanged!
        }

        // 5. Update balances
        BigDecimal converted = amount.multiply(quote.getRate()).subtract(quote.getFees());
        account.debit(from, amount);
        account.credit(to, converted);

        accountRepo.save(account);

        // 6. Method completes successfully → Transaction COMMIT
        // Balance changes are now permanent!

        return new ConvertResponse(converted, quote.getRate(), quote.getFees(), from, to, amount);
    }
}
```

**Scenarios:**

✅ **Happy path**: Exchange succeeds → Balances updated → COMMIT
❌ **Insufficient funds**: Exception at step 2 → ROLLBACK (no changes)
❌ **Exchange fails**: Exception at step 4 → ROLLBACK (original balances restored)

---

## Best Practices

1. **Keep transactions short** - Long transactions lock resources
2. **Use read-only when possible** - Better performance
3. **Let exceptions propagate** - Don't catch and hide them
4. **Be careful with external calls** - They can't be rolled back!
5. **Test rollback scenarios** - Ensure data integrity

---

## Common Pitfalls

### ❌ Calling @Transactional method from same class

```java
@Service
public class UserService {

    public void publicMethod() {
        this.transactionalMethod();  // Doesn't work!
    }

    @Transactional
    private void transactionalMethod() {
        // @Transactional is IGNORED
    }
}
```

**Why?** Spring uses proxies for `@Transactional`. Internal calls bypass the proxy.

### ❌ External API calls in transactions

```java
@Transactional
public void processOrder(Order order) {
    orderRepo.save(order);  // Can rollback ✅
    emailService.sendEmail();  // Can't rollback! ❌

    // If exception occurs after email is sent,
    // database rolls back but email is already sent!
}
```

**Solution**: Use patterns like Outbox (covered in Stage 2)

---

## Quick Reference

```java
// Basic transaction
@Transactional
public void method() { }

// Read-only
@Transactional(readOnly = true)
public User getUser() { }

// Custom rollback
@Transactional(rollbackFor = Exception.class)
public void method() throws Exception { }

// New transaction
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void method() { }
```

---

Now you understand how transactions ensure data integrity in financial applications! In Stage 2, we'll learn about distributed transactions and the Saga pattern.
