# Event Sourcing

**A comprehensive guide to Event Sourcing for financial systems**

---

## Table of Contents

1. [What is Event Sourcing?](#what-is-event-sourcing)
2. [Why Use Event Sourcing?](#why-use-event-sourcing)
3. [Core Concepts](#core-concepts)
4. [Implementation in Java/Spring Boot](#implementation-in-javaspring-boot)
5. [Event Store Design](#event-store-design)
6. [Rebuilding State from Events](#rebuilding-state-from-events)
7. [Snapshots](#snapshots)
8. [Trade-offs](#trade-offs)

---

## What is Event Sourcing?

**Event Sourcing** is a pattern where you store the **history of changes** (events) instead of just the current state.

### Traditional Approach (State-Based)

```
Current State Only:
┌─────────────────────────┐
│ Account Balance: $500   │  ← Only this is stored
└─────────────────────────┘

History lost:
- Who made changes?
- When did balance become $500?
- What transactions occurred?
```

### Event Sourcing Approach

```
Event Stream (Immutable Log):
1. AccountCreated (balance: $1000)
2. MoneyWithdrawn (amount: $200, balance: $800)
3. MoneyDeposited (amount: $100, balance: $900)
4. MoneyWithdrawn (amount: $400, balance: $500)

Current State = Replay all events
```

**Key Insight:** Instead of UPDATE, we only INSERT new events.

---

## Why Use Event Sourcing?

### 1. Complete Audit Trail

**Problem:** Bank needs to prove transaction history for regulatory compliance.

```java
// Traditional approach
public void withdraw(Long accountId, BigDecimal amount) {
    Account account = accountRepo.findById(accountId).get();
    account.setBalance(account.getBalance().subtract(amount));
    accountRepo.save(account); // Old balance lost!
}

// Event sourcing approach
public void withdraw(Long accountId, BigDecimal amount) {
    MoneyWithdrawnEvent event = new MoneyWithdrawnEvent(
        accountId, 
        amount, 
        LocalDateTime.now()
    );
    eventStore.append(event); // History preserved forever!
}
```

### 2. Time Travel (Reconstruct Past State)

```java
// "What was the balance on January 1, 2024?"
public BigDecimal getBalanceAt(Long accountId, LocalDateTime pointInTime) {
    List<Event> events = eventStore.getEventsUntil(accountId, pointInTime);
    
    BigDecimal balance = BigDecimal.ZERO;
    for (Event event : events) {
        if (event instanceof MoneyDepositedEvent) {
            balance = balance.add(event.getAmount());
        } else if (event instanceof MoneyWithdrawnEvent) {
            balance = balance.subtract(event.getAmount());
        }
    }
    return balance;
}
```

### 3. Debugging & Analytics

```java
// "How many times did this customer withdraw money this month?"
List<Event> events = eventStore.getEvents(accountId);
long withdrawalCount = events.stream()
    .filter(e -> e instanceof MoneyWithdrawnEvent)
    .filter(e -> e.getTimestamp().getMonth() == Month.JANUARY)
    .count();
```

### 4. Event-Driven Architecture

```java
@EventHandler
public void on(MoneyWithdrawnEvent event) {
    // Automatically triggers:
    // - Send email notification
    // - Update analytics dashboard
    // - Check fraud rules
    // - Update cache
}
```

---

## Core Concepts

### 1. Events (Immutable Facts)

```java
public abstract class DomainEvent {
    private final String eventId;
    private final Long aggregateId;  // Which account/order/user
    private final Long version;      // Sequence number
    private final LocalDateTime timestamp;
    
    // Events are IMMUTABLE (final fields, no setters)
}

public class AccountCreatedEvent extends DomainEvent {
    private final String accountNumber;
    private final Currency currency;
    private final BigDecimal initialBalance;
}

public class MoneyWithdrawnEvent extends DomainEvent {
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String reason;
}
```

### 2. Aggregate (Entity that Produces Events)

```java
public class Account {
    private Long id;
    private BigDecimal balance;
    private List<DomainEvent> uncommittedEvents = new ArrayList<>();
    
    // Command (intent to do something)
    public void withdraw(BigDecimal amount) {
        // 1. Validate
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        
        // 2. Create event (fact that something happened)
        MoneyWithdrawnEvent event = new MoneyWithdrawnEvent(
            id, 
            amount,
            balance.subtract(amount)
        );
        
        // 3. Apply event to change state
        apply(event);
        
        // 4. Add to uncommitted events (will be saved later)
        uncommittedEvents.add(event);
    }
    
    // Apply event to update state
    private void apply(MoneyWithdrawnEvent event) {
        this.balance = event.getBalanceAfter();
    }
    
    public List<DomainEvent> getUncommittedEvents() {
        return uncommittedEvents;
    }
    
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }
}
```

### 3. Event Store (Database for Events)

```java
public interface EventStore {
    void append(Long aggregateId, List<DomainEvent> events, long expectedVersion);
    List<DomainEvent> getEvents(Long aggregateId);
    List<DomainEvent> getEventsFrom(Long aggregateId, long version);
}
```

---

## Implementation in Java/Spring Boot

### Event Store Schema

```sql
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (aggregate_id, version)  -- Prevent duplicate events
);

CREATE INDEX idx_aggregate_events ON events(aggregate_id, version);
CREATE INDEX idx_event_type ON events(event_type, timestamp);
```

### Event Store Implementation

```java
@Repository
public class PostgresEventStore implements EventStore {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public void append(Long aggregateId, List<DomainEvent> events, long expectedVersion) {
        // Optimistic concurrency check
        Long currentVersion = getCurrentVersion(aggregateId);
        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException("Events already appended by another process");
        }
        
        String sql = 
            "INSERT INTO events (aggregate_id, aggregate_type, event_type, version, payload, timestamp) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?)";
        
        long nextVersion = expectedVersion + 1;
        for (DomainEvent event : events) {
            String payload = objectMapper.writeValueAsString(event);
            
            jdbcTemplate.update(sql,
                aggregateId,
                event.getAggregateType(),
                event.getClass().getSimpleName(),
                nextVersion,
                payload,
                event.getTimestamp()
            );
            
            nextVersion++;
        }
    }
    
    @Override
    public List<DomainEvent> getEvents(Long aggregateId) {
        String sql = 
            "SELECT event_type, payload, version, timestamp " +
            "FROM events " +
            "WHERE aggregate_id = ? " +
            "ORDER BY version ASC";
        
        return jdbcTemplate.query(sql, new Object[]{aggregateId}, (rs, rowNum) -> {
            String eventType = rs.getString("event_type");
            String payload = rs.getString("payload");
            
            // Deserialize to concrete event class
            Class<?> eventClass = Class.forName("com.learn.events." + eventType);
            return (DomainEvent) objectMapper.readValue(payload, eventClass);
        });
    }
    
    private Long getCurrentVersion(Long aggregateId) {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM events WHERE aggregate_id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, aggregateId);
    }
}
```

### Repository Pattern

```java
@Repository
public class AccountRepository {
    @Autowired
    private EventStore eventStore;
    
    public Account findById(Long accountId) {
        List<DomainEvent> events = eventStore.getEvents(accountId);
        
        if (events.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        
        // Rebuild account from events
        Account account = new Account();
        for (DomainEvent event : events) {
            account.apply(event);  // Replay history
        }
        
        return account;
    }
    
    public void save(Account account) {
        List<DomainEvent> uncommitted = account.getUncommittedEvents();
        
        if (uncommitted.isEmpty()) {
            return; // No changes
        }
        
        long expectedVersion = account.getVersion();
        eventStore.append(account.getId(), uncommitted, expectedVersion);
        
        account.markEventsAsCommitted();
    }
}
```

### Service Layer

```java
@Service
public class AccountService {
    @Autowired
    private AccountRepository accountRepo;
    
    @Transactional
    public void withdraw(Long accountId, BigDecimal amount) {
        // 1. Load account (replay events)
        Account account = accountRepo.findById(accountId);
        
        // 2. Execute command (generates events)
        account.withdraw(amount);
        
        // 3. Save events
        accountRepo.save(account);
        
        // 4. Publish events to other services (optional)
        publishEvents(account.getUncommittedEvents());
    }
    
    private void publishEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            kafkaTemplate.send("domain-events", event);
        }
    }
}
```

---

## Rebuilding State from Events

### Projection (Read Model)

Instead of replaying events on every read, maintain a **projection** (denormalized view).

```java
// Write side: Event Store (source of truth)
events table:
1. AccountCreated (balance: 1000)
2. MoneyWithdrawn (amount: 200)
3. MoneyDeposited (amount: 100)

// Read side: Projection (for fast queries)
account_projections table:
┌────────────┬──────────┐
│ account_id │ balance  │
├────────────┼──────────┤
│ 123        │ 900      │
└────────────┴──────────┘
```

### Projection Updater

```java
@Component
public class AccountProjectionUpdater {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @KafkaListener(topics = "domain-events")
    public void updateProjection(DomainEvent event) {
        if (event instanceof AccountCreatedEvent) {
            handleAccountCreated((AccountCreatedEvent) event);
        } else if (event instanceof MoneyWithdrawnEvent) {
            handleMoneyWithdrawn((MoneyWithdrawnEvent) event);
        } else if (event instanceof MoneyDepositedEvent) {
            handleMoneyDeposited((MoneyDepositedEvent) event);
        }
    }
    
    private void handleAccountCreated(AccountCreatedEvent event) {
        String sql = "INSERT INTO account_projections (account_id, balance, currency) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, event.getAggregateId(), event.getInitialBalance(), event.getCurrency());
    }
    
    private void handleMoneyWithdrawn(MoneyWithdrawnEvent event) {
        String sql = "UPDATE account_projections SET balance = balance - ? WHERE account_id = ?";
        jdbcTemplate.update(sql, event.getAmount(), event.getAggregateId());
    }
    
    private void handleMoneyDeposited(MoneyDepositedEvent event) {
        String sql = "UPDATE account_projections SET balance = balance + ? WHERE account_id = ?";
        jdbcTemplate.update(sql, event.getAmount(), event.getAggregateId());
    }
}
```

### Query Service (Read from Projection)

```java
@Service
public class AccountQueryService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public BigDecimal getBalance(Long accountId) {
        String sql = "SELECT balance FROM account_projections WHERE account_id = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId);
    }
    
    public List<Account> findAccountsWithBalanceGreaterThan(BigDecimal amount) {
        String sql = "SELECT * FROM account_projections WHERE balance > ?";
        return jdbcTemplate.query(sql, new Object[]{amount}, (rs, rowNum) -> {
            // Map to Account DTO
        });
    }
}
```

---

## Snapshots

**Problem:** Replaying 1 million events is slow.

**Solution:** Periodically save a **snapshot** of current state.

```java
CREATE TABLE snapshots (
    aggregate_id BIGINT PRIMARY KEY,
    aggregate_type VARCHAR(100),
    version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### Snapshot Creation

```java
@Service
public class SnapshotService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    public void createSnapshots() {
        List<Long> accountIds = getActiveAccountIds();
        
        for (Long accountId : accountIds) {
            Account account = accountRepo.findById(accountId);
            
            // Save snapshot every 1000 events
            if (account.getVersion() % 1000 == 0) {
                saveSnapshot(account);
            }
        }
    }
    
    private void saveSnapshot(Account account) {
        String payload = objectMapper.writeValueAsString(account);
        
        String sql = 
            "INSERT INTO snapshots (aggregate_id, aggregate_type, version, payload, created_at) " +
            "VALUES (?, ?, ?, ?::jsonb, ?) " +
            "ON CONFLICT (aggregate_id) DO UPDATE SET " +
            "version = EXCLUDED.version, payload = EXCLUDED.payload, created_at = EXCLUDED.created_at";
        
        jdbcTemplate.update(sql, 
            account.getId(), 
            "Account", 
            account.getVersion(), 
            payload, 
            LocalDateTime.now()
        );
    }
}
```

### Loading with Snapshot

```java
public Account findById(Long accountId) {
    // 1. Try to load snapshot
    Optional<AccountSnapshot> snapshot = loadSnapshot(accountId);
    
    Account account;
    long fromVersion;
    
    if (snapshot.isPresent()) {
        // Start from snapshot
        account = snapshot.get().toAccount();
        fromVersion = snapshot.get().getVersion();
    } else {
        // Start from scratch
        account = new Account(accountId);
        fromVersion = 0;
    }
    
    // 2. Load events after snapshot
    List<DomainEvent> events = eventStore.getEventsFrom(accountId, fromVersion);
    
    // 3. Replay events
    for (DomainEvent event : events) {
        account.apply(event);
    }
    
    return account;
}
```

**Performance:**
- Without snapshot: Replay 10,000 events (500ms)
- With snapshot: Replay 100 events (5ms) ← 100x faster!

---

## Trade-offs

### Pros ✅

**1. Complete Audit Trail:**
- Every change is recorded
- Perfect for financial/healthcare/compliance systems

**2. Time Travel:**
- Reconstruct state at any point in time
- Debug issues by replaying events

**3. Event-Driven Architecture:**
- Easy to add new projections/features
- Loosely coupled services

**4. No Data Loss:**
- Events are immutable (never UPDATE/DELETE)
- Can rebuild any state from events

### Cons ❌

**1. Complexity:**
- Harder to understand than CRUD
- Need to manage projections

**2. Eventual Consistency:**
- Projections may lag behind events
- Can't query latest state instantly

**3. Schema Evolution:**
- Event schema changes are hard
- Need versioning strategy

**4. Storage:**
- Events grow forever
- Need archiving strategy

---

## When to Use Event Sourcing

### Good Use Cases ✅

**1. Financial Systems:**
- Bank accounts, payments, ledgers
- Need complete audit trail

**2. Compliance-Heavy Domains:**
- Healthcare records
- Legal documents
- Government systems

**3. Complex Business Logic:**
- Workflow systems
- State machines

**4. Analytics/Reporting:**
- Need to analyze historical trends
- Business intelligence

### Not Suitable ❌

**1. Simple CRUD:**
- Basic content management
- User profiles

**2. High-Volume, Low-Value Data:**
- Sensor readings
- Logs

**3. When Immediate Consistency Required:**
- Real-time inventory
- Seat reservations (unless carefully designed)

---

## Summary

**Event Sourcing:**
✅ Store events (facts) instead of current state  
✅ Complete audit trail & time travel  
✅ Event-driven architecture  
✅ Perfect for financial systems

**Core Pattern:**
```java
Command → Aggregate → Events → Event Store
                           ↓
                      Projections
```

**Key Concepts:**
- **Events**: Immutable facts
- **Aggregates**: Produce events
- **Event Store**: Database for events
- **Projections**: Denormalized views for queries
- **Snapshots**: Performance optimization

**Next Steps:**
- Read [cqrs.md](cqrs.md) for separating reads and writes
- Study our payment system's event store implementation
- Practice event modeling for your domain
