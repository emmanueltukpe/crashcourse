# Database Locking and Isolation Levels

A practical guide to preventing data corruption in concurrent database access, with real-world examples from our payment system.

## Table of Contents

1 [The Concurrency Problem](#the-concurrency-problem) 2. [Optimistic Locking](#optimistic-locking) 3. [Pessimistic Locking](#pessimistic-locking) 4. [Isolation Levels](#isolation-levels) 5. [PostgreSQL Specifics](#postgresql-specifics) 6. [Decision Matrix](#decision-matrix)

---

## The Concurrency Problem

### Scenario: Concurrent Withdrawals

```
Account balance: $1000

Thread 1:                  Thread 2:
Read balance ($1000)       Read balance ($1000)
Withdraw $600              Withdraw $600
Save ($400)                Save ($400)

Final balance: $400 ← WRONG! Should be -$200 or one should fail
```

**Problems:**

1. **Lost Update**: Thread 2's update overwrites Thread 1's
2. **Double-Spend**: Both withdrawals succeed despite insufficient funds

**Solution**: Use locking mechanisms!

---

## Optimistic Locking

### Concept

**Optimistic**: Assume conflicts are RARE. Check for conflicts at commit time.

```
1. Read data (remember version)
2. Do your work
3. Try to save
   - If nobody else modified it: Success! ✓
   - If someone modified it: Conflict! Retry ✗
```

### Implementation with @Version

```java
@Entity
public class Account {
    @Id
    private Long id;

    private BigDecimal balance;

    @Version  // ← Magic happens here!
    private Long version;
}
```

**How it works:**

```sql
-- Thread 1 reads account
SELECT * FROM accounts WHERE id = 1;
-- id=1, balance=1000, version=5

-- Thread 2 reads account (same time)
SELECT * FROM accounts WHERE id = 1;
-- id=1, balance=1000, version=5  ← Same version!

-- Thread 1 tries to save
UPDATE accounts
SET balance = 400, version = 6
WHERE id = 1 AND version = 5;
-- Success! 1 row updated

-- Thread 2 tries to save
UPDATE accounts
SET balance = 400, version = 6
WHERE id = 1 AND version = 5;  ← version is now 6, not 5!
-- Failure! 0 rows updated → OptimisticLockException
```

### Retry Logic

```java
@Service
public class AccountService {
    private static final int MAX_RETRIES = 3;

    public void withdraw(Long accountId, BigDecimal amount) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                withdrawWithOptimisticLock(accountId, amount);
                return; // Success!

            } catch (OptimisticLockException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Too many concurrent updates", e);
                }
                // Brief pause before retry
                Thread.sleep(50 * attempt);
                // Loop retries
            }
        }
    }

    @Transactional
    protected void withdrawWithOptimisticLock(Long accountId, BigDecimal amount) {
        // Read without locking
        Account account = accountRepository.findById(accountId)
            .orElseThrow();

        // Check balance
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        // Update
        account.setBalance(account.getBalance().subtract(amount));

        // Save - optimistic lock check happens here!
        accountRepository.save(account);
    }
}
```

### Pros and Cons

**Pros:**
✅ Better throughput (no locks held)  
✅ No deadlocks  
✅ Good for low-contention scenarios  
✅ Simpler to understand

**Cons:**
❌ Need retry logic  
❌ Work might be wasted (rollback on conflict)  
❌ Not suitable for high-contention  
❌ Can fail after doing expensive operations

---

## Pessimistic Locking

### Concept

**Pessimistic**: Assume conflicts are COMMON. Lock immediately.

```
1. SELECT ... FOR UPDATE (lock acquired)
2. Other threads WAIT for lock
3. Do your work
4. Save and release lock
```

### Implementation

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
```

**SQL generated:**

```sql
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;
```

**What `FOR UPDATE` does:**

``` text
Thread 1: SELECT ... FOR UPDATE  ← Acquires lock
          (lock held)

Thread 2: SELECT ... FOR UPDATE  ← Waits for lock
          (blocked)

Thread 1: UPDATE ...
          COMMIT                 ← Releases lock

Thread 2:                        ← Now gets lock
          UPDATE ...
          COMMIT
```

### Lock Modes

```java
// PESSIMISTIC_READ: Shared lock (multiple readers OK, writers wait)
@Lock(LockModeType.PESSIMISTIC_READ)
Optional<Account> findByIdForRead(Long id);

// PESSIMISTIC_WRITE: Exclusive lock (everyone waits)
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Account> findByIdForUpdate(Long id);

// PESSIMISTIC_FORCE_INCREMENT: Write lock + increment version
@Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
Optional<Account> findByIdWithVersionIncrement(Long id);
```

### Timeout

```java
// Don't wait forever!
@QueryHints({
    @QueryHint(name = "javax.persistence.lock.timeout", value = "5000") // 5 seconds
})
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Account> findByIdForUpdate(Long id);
```

### Pros and Cons

**Pros:**
✅ Guaranteed no conflicts  
✅ No retry logic needed  
✅ Good for high-contention scenarios  
✅ Predictable behavior

**Cons:**
❌ Lower throughput (locks block threads)  
❌ Deadlock potential  
❌ Locks held during entire transaction  
❌ Can cause performance bottlenecks

---

## Isolation Levels

**Isolation**: How much one transaction's changes are visible to others.

### The Four Levels

#### 1. READ UNCOMMITTED (Lowest isolation)

```sql
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
```

**What it allows:**

- Read uncommitted changes from other transactions (**dirty reads**)

```
Transaction 1:                Transaction 2:
UPDATE accounts               SELECT * FROM accounts
SET balance = 500             WHERE id = 1;
WHERE id = 1;                 → Sees 500 (uncommitted!)
(not committed yet)
ROLLBACK;                     ← Transaction 1 rolls back
                              but Transaction 2 saw invalid data!
```

**When to use:** Almost never. Data can be inconsistent.

#### 2. READ COMMITTED (PostgreSQL default)

```sql
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

**What it prevents:**

- Dirty reads ✓

**What it allows:**

- Non-repeatable reads

```
Transaction 1:                Transaction 2:
SELECT balance
FROM accounts
WHERE id = 1;
→ Returns 1000
                              UPDATE accounts
                              SET balance = 500
                              WHERE id = 1;
                              COMMIT;
SELECT balance
FROM accounts
WHERE id = 1;
→ Returns 500                 ← Different value!
```

**When to use:** Most applications. Good default.

#### 3. REPEATABLE READ

```sql
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
```

**What it prevents:**

- Dirty reads ✓
- Non-repeatable reads ✓

**What it allows:**

- Phantom reads

```
Transaction 1:                Transaction 2:
SELECT COUNT(*)
FROM accounts
WHERE balance > 1000;
→ Returns 5
                              INSERT INTO accounts
                              VALUES (6, 2000);
                              COMMIT;
SELECT COUNT(*)
FROM accounts
WHERE balance > 1000;
→ Returns 6                   ← New row appeared!
```

**When to use:** When you need consistent view of data.

#### 4. SERIALIZABLE (Highest isolation)

```sql
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
```

**What it prevents:**

- Dirty reads ✓
- Non-repeatable reads ✓
- Phantom reads ✓

**How:** Transactions appear to run sequentially (even if concurrent).

```
Transaction 1:                Transaction 2:
BEGIN;                        BEGIN;
SELECT SUM(balance)           UPDATE accounts
FROM accounts;                SET balance = balance + 100;
                              COMMIT;  ← Might fail with serialization error
```

**When to use:** Critical financial operations. Slowest but safest.

### Setting Isolation Level in Spring

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalOperation() {
    // Highest isolation
}

@Transactional(isolation = Isolation.READ_COMMITTED)
public void normalOperation() {
    // Default (PostgreSQL)
}
```

### Isolation Level Comparison

| Level            | Dirty Read  | Non-Repeatable Read | Phantom Read | Performance |
| ---------------- | ----------- | ------------------- | ------------ | ----------- |
| READ UNCOMMITTED | ✗ Possible  | ✗ Possible          | ✗ Possible   | Fastest     |
| READ COMMITTED   | ✓ Prevented | ✗ Possible          | ✗ Possible   | Fast        |
| REPEATABLE READ  | ✓ Prevented | ✓ Prevented         | ✗ Possible   | Slower      |
| SERIALIZABLE     | ✓ Prevented | ✓ Prevented         | ✓ Prevented  | Slowest     |

---

## PostgreSQL Specifics

### MVCC (Multi-Version Concurrency Control)

PostgreSQL uses **MVCC** - keeps multiple versions of rows.

```
Transaction 1 reads row (version 1)
Transaction 2 updates row (creates version 2, commits)
Transaction 1 still sees version 1 (until it commits/rollbacks)
```

**Benefits:**

- Readers don't block writers
- Writers don't block readers
- Better concurrency than traditional locking

### SELECT FOR UPDATE Variants

```sql
-- Standard: Wait for lock
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;

-- NOWAIT: Don't wait, fail immediately
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;

-- SKIP LOCKED: Skip locked rows
SELECT * FROM queue FOR UPDATE SKIP LOCKED LIMIT 1;
```

**SKIP LOCKED example (job queue):**

```java
// Multiple workers can process queue concurrently
@Query(value = "SELECT * FROM job_queue " +
               "WHERE status = 'PENDING' " +
               "ORDER BY created_at " +
               "FOR UPDATE SKIP LOCKED " +
               "LIMIT 1",
       nativeQuery = true)
Optional<Job> getNextJob();

// Worker 1 locks job A
// Worker 2 skips job A, locks job B
// No waiting!
```

### Advisory Locks

Application-level locks (not tied to rows):

```sql
-- Try to acquire lock (returns true/false)
SELECT pg_try_advisory_lock(12345);

-- Release lock
SELECT pg_advisory_unlock(12345);
```

**Use case: Distributed cron jobs**

```java
@Scheduled(cron = "0 0 * * * *")
public void hourlyJob() {
    // Try to acquire lock (prevents multiple instances running same job)
    Boolean acquired = jdbcTemplate.queryForObject(
        "SELECT pg_try_advisory_lock(?)",
        Boolean.class,
        JOB_LOCK_ID
    );

    if (!acquired) {
        logger.info("Another instance is running this job");
        return;
    }

    try {
        // Do work
    } finally {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + JOB_LOCK_ID + ")");
    }
}
```

---

## Decision Matrix

### When to Use Optimistic Locking

✅ **Use when:**

- Low contention (conflicts rare)
- Read-heavy workload
- Long-running transactions
- Need best throughput

**Example scenarios:**

- User profile updates
- Blog post edits
- Product catalog updates

```java
@Entity
public class BlogPost {
    @Version
    private Long version;

    // Optimistic locking perfect here - rare conflicts
}
```

### When to Use Pessimistic Locking

✅ **Use when:**

- High contention (many concurrent updates)
- Critical operations (can't afford retries)
- Short transactions
- Financial operations

**Example scenarios:**

- Bank withdrawals
- Inventory management
- Ticket booking
- Seat reservation

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Seat> findAvailableSeatForUpdate(Long eventId);
// Must lock - can't double-book seats!
```

### Hybrid Approach

```java
@Service
public class AccountService {

    // Normal operations: Optimistic (better performance)
    @Transactional
    public void updateProfile(Long userId, Profile profile) {
        Account account = accountRepo.findById(userId).get();
        account.setProfile(profile);
account.save(); // Version check
    }

    // Critical operations: Pessimistic (guaranteed consistency)
    @Transactional
    public void withdraw(Long userId, BigDecimal amount) {
        Account account = accountRepo.findByIdForUpdate(userId).get();
        account.debit(amount);
        accountRepo.save(account);
    }
}
```

### Decision Tree

```
Need to update database row?
│
├─ High contention expected?
│  ├─ YES → Use PESSIMISTIC locking
│  │        (SELECT FOR UPDATE)
│  │
│  └─ NO → Low contention expected?
│         ├─ YES → Use OPTIMISTIC locking
│         │        (@Version)
│         │
│         └─ UNSURE → Start with OPTIMISTIC
│                      Monitor conflicts
│                      Switch to PESSIMISTIC if needed
│
└─ Read-only?
   → No locking needed
     (But consider isolation level)
```

---

## Common Patterns

### Pattern 1: Account Balance Update

```java
// PESSIMISTIC (recommended for money!)
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepo.findByIdForUpdate(fromId).get();
    Account to = accountRepo.findByIdForUpdate(toId).get();

    from.debit(amount);
    to.credit(amount);

    accountRepo.save(from);
    accountRepo.save(to);
}
```

### Pattern 2: Inventory Decrement

```java
// PESSIMISTIC (prevent overselling)
@Transactional
public void purchaseItem(Long itemId, int quantity) {
    Product product = productRepo.findByIdForUpdate(itemId).get();

    if (product.getStock() < quantity) {
        throw new OutOfStockException();
    }

    product.setStock(product.getStock() - quantity);
    productRepo.save(product);
}
```

### Pattern 3:Blog Post Edit

```java
// OPTIMISTIC (conflicts rare)
@Entity
public class BlogPost {
    @Version
    private Long version;
}

@Transactional
public void updatePost(Long postId, String content) {
    BlogPost post = postRepo.findById(postId).get();
    post.setContent(content);
    postRepo.save(post); // If someone else edited, retry
}
```

---

## Testing Concurrency

```java
@Test
public void testConcurrentWithdrawals() throws Exception {
    // Create account with $1000
    Account account = accountService.create(userId, new BigDecimal("1000"));

    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(10);
    AtomicInteger failures = new AtomicInteger(0);

    // 10 threads try to withdraw $200 each
    for (int i = 0; i < 10; i++) {
        executor.submit(() -> {
            try {
                accountService.withdraw(userId, new BigDecimal("200"));
            } catch (InsufficientFundsException e) {
                failures.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executor.shutdown();

    // Only 5 should succeed ($1000 / $200 = 5)
    assertEquals(5, failures.get());

    Account final Account = accountService.get(userId);
    assertEquals(new BigDecimal("0.00"), finalAccount.getBalance());
}
```

---

## Summary

**Key Takeaways:**

✅ **Optimistic Locking**: Check for conflicts at commit (@Version)  
✅ **Pessimistic Locking**: Lock immediately (SELECT FOR UPDATE)  
✅ **Isolation Levels**: Control visibility of concurrent changes  
✅ **MVCC**: PostgreSQL's efficient concurrency mechanism  
✅ **Decision**: Low contention → Optimistic, High contention → Pessimistic

**For Our Payment System:**

- Account balance: **Pessimistic** (critical, high contention)
- Payment records: **Optimistic** (lower contention)
- User profiles: **Optimistic** (rare updates)

**Next Steps:**

- Review [AccountService.java](/Users/emmanuel/Downloads/crashcourse/account-service/src/main/java/com/learn/accountservice/service/AccountService.java) - both strategies implemented
- Run [ConcurrencyTest.java](/Users/emmanuel/Downloads/crashcourse/account-service/src/test/java/com/learn/accountservice/ConcurrencyTest.java) - see them in action
- Read [scaling-strategy.md](scaling-strategy.md) for horizontal scaling

Database concurrency is critical for data integrity! 🔒
