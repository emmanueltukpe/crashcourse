# Backend Engineering Interview Guide

**Comprehensive preparation for Moniepoint and similar fintech backend engineering roles**

---

## Table of Contents

1. [Company Context: Moniepoint](#company-context-moniepoint)
2. [System Design Questions](#system-design-questions)
3. [Distributed Systems & Microservices](#distributed-systems--microservices)
4. [Database & Data Management](#database--data-management)
5. [Performance & Scalability](#performance--scalability)
6. [Code Design & Patterns](#code-design--patterns)
7. [Behavioral Questions](#behavioral-questions)
8. [Questions to Ask](#questions-to-ask)

---

## Company Context: Moniepoint

### Key Facts

- **Scale**: 3M+ users, $182B processed in 2023
- **Market**: Majority of POS transactions in Nigeria
- **Products**: Personal/business banking, payments, credit, business management
- **Tech Stack**: Java, Spring Boot, Kubernetes, Docker, PostgreSQL, DynamoDB, Elasticsearch, AWS
- **Growth**: Second-fastest growing company in Africa (since 2019)

### What They Look For

✅ **5+ years** Java & Spring Boot experience  
✅ Strong understanding of **microservices architecture**  
✅ Experience with **high-throughput payment systems**  
✅ **Test-driven development** mindset  
✅ Knowledge of **distributed transactions** and **data consistency**  
✅ Familiarity with **AWS**, **Docker**, **Kubernetes**  
✅ Problem-solving ability at scale (millions of transactions)

---

## System Design Questions

### Question 1: Design a POS Payment System

**Requirements:**

- Handle 10,000 transactions per second
- Support offline transactions (sync when online)
- Ensure no double-charging
- Real-time fraud detection
- Must handle network failures gracefully

**Model Answer:**

#### 1. High-Level Architecture

```
┌─────────────────┐
│   POS Device    │ (Offline capable)
│   - Local DB    │
│   - Queue       │
└────────┬────────┘
         │ HTTPS
         ↓
┌─────────────────────────────────────────┐
│         API Gateway (Rate Limiting)      │
└────────┬────────────────────────────────┘
         │
    ┌────┴────┬────────┬───────────┐
    ↓         ↓        ↓           ↓
┌─────────┐ ┌────────┐ ┌─────────┐ ┌──────────┐
│Payment  │ │Account │ │Fraud    │ │Settlement│
│Service  │ │Service │ │Service  │ │Service   │
└────┬────┘ └───┬────┘ └────┬────┘ └────┬─────┘
     │          │           │           │
     └──────────┴───────────┴───────────┘
                    │
         ┌──────────┴──────────┐
         ↓                     ↓
    ┌─────────┐          ┌─────────┐
    │PostgreSQL│         │  Kafka  │
    │(Primary) │         │(Events) │
    └─────────┘          └─────────┘
```

#### 2. Key Design Decisions

**Offline Capability:**

```java
// POS Device Logic
@Service
public class OfflinePaymentHandler {
    private LocalDatabase localDb;
    private SyncQueue syncQueue;

    public PaymentResult processPayment(PaymentRequest request) {
        // 1. Store locally with PENDING status
        String txnId = UUID.randomUUID().toString();
        localDb.save(new LocalTransaction(txnId, request, Status.PENDING));

        // 2. Try to process online
        if (isOnline()) {
            try {
                PaymentResponse response = apiClient.process(request);
                localDb.updateStatus(txnId, Status.CONFIRMED);
                return PaymentResult.success(response);
            } catch (NetworkException e) {
                // 3. Queue for later sync
                syncQueue.enqueue(txnId);
                return PaymentResult.pending(txnId);
            }
        } else {
            // 4. Offline mode - queue for sync
            syncQueue.enqueue(txnId);
            return PaymentResult.pending(txnId);
        }
    }

    @Scheduled(fixedDelay = 30000) // Every 30s
    public void syncPendingTransactions() {
        if (!isOnline()) return;

        List<String> pending = syncQueue.getAll();
        for (String txnId : pending) {
            LocalTransaction txn = localDb.get(txnId);
            try {
                PaymentResponse response = apiClient.process(txn.getRequest());
                localDb.updateStatus(txnId, Status.CONFIRMED);
                syncQueue.remove(txnId);
            } catch (Exception e) {
                // Retry later
                logger.warn("Sync failed for {}", txnId, e);
            }
        }
    }
}
```

**Idempotency (Prevent Double-Charging):**

```java
@Service
public class PaymentService {
    @Autowired
    private IdempotencyRepository idempotencyRepo;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey(); // From POS

        // Check if already processed
        Optional<IdempotencyRecord> existing =
            idempotencyRepo.findByKey(idempotencyKey);

        if (existing.isPresent()) {
            if (existing.get().getStatus() == Status.COMPLETED) {
                // Already processed successfully - return same result
                return existing.get().getResponse();
            } else if (existing.get().getStatus() == Status.PROCESSING) {
                // Currently processing - return retry-later
                throw new ConcurrentProcessingException();
            }
        }

        // Create idempotency record (PROCESSING status)
        IdempotencyRecord record = new IdempotencyRecord(
            idempotencyKey,
            Status.PROCESSING,
            LocalDateTime.now()
        );
        idempotencyRepo.save(record);

        try {
            // Process payment
            PaymentResponse response = executePayment(request);

            // Update idempotency record (COMPLETED status)
            record.setStatus(Status.COMPLETED);
            record.setResponse(response);
            idempotencyRepo.save(record);

            return response;
        } catch (Exception e) {
            // Mark as FAILED
            record.setStatus(Status.FAILED);
            record.setError(e.getMessage());
            idempotencyRepo.save(record);
            throw e;
        }
    }
}
```

**Database Schema:**

```sql
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    request_payload JSONB NOT NULL,
    response_payload JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_created_at (created_at)  -- For cleanup
);

-- Cleanup old records (after 7 days)
DELETE FROM idempotency_records
WHERE created_at < NOW() - INTERVAL '7 days'
AND status IN ('COMPLETED', 'FAILED');
```

**Real-Time Fraud Detection:**

```java
@Service
public class FraudDetectionService {
    @Autowired
    private RedisTemplate<String, Integer> redis;

    public FraudCheckResult checkTransaction(PaymentRequest request) {
        String merchantId = request.getMerchantId();
        String cardHash = hash(request.getCardNumber());

        // Rule 1: Velocity check (5 transactions in 1 minute)
        String velocityKey = "velocity:" + cardHash;
        Long count = redis.opsForValue().increment(velocityKey);
        if (count == 1) {
            redis.expire(velocityKey, 60, TimeUnit.SECONDS);
        }
        if (count > 5) {
            return FraudCheckResult.reject("Velocity exceeded");
        }

        // Rule 2: Amount spike (>3x average)
        BigDecimal avgAmount = getAverageAmount(merchantId);
        if (request.getAmount().compareTo(avgAmount.multiply(new BigDecimal("3"))) > 0) {
            return FraudCheckResult.review("Amount spike detected");
        }

        // Rule 3: Geo-location anomaly
        String lastLocation = getLastTransactionLocation(cardHash);
        if (isSuspiciousLocationChange(lastLocation, request.getLocation())) {
            return FraudCheckResult.review("Suspicious location");
        }

        return FraudCheckResult.approve();
    }
}
```

#### 3. Scalability Strategy

**Horizontal Scaling:**

- Stateless services (12-factor app)
- Load balancer (Round-robin or least connections)
- Auto-scaling based on CPU/memory metrics

**Database Scaling:**

- **Read Replicas**: Route read queries to replicas
- **Partitioning**: Partition by merchant_id or region
- **Caching**: Redis for hot data (merchant info, fraud rules)

**Kafka for Async Processing:**

```
Payment Accepted → Kafka Topic "payment-events"
├→ Settlement Service (batch settlements)
├→ Analytics Service (real-time dashboards)
├→ Notification Service (SMS/email receipts)
└→ Ledger Service (accounting)
```

#### 4. Trade-offs Discussed

| Decision             | Pro                       | Con                               |
| -------------------- | ------------------------- | --------------------------------- |
| Offline queue on POS | Works without network     | Risk of data loss if device fails |
| Idempotency in DB    | Strong consistency        | Extra DB round-trip               |
| Real-time fraud      | Prevent fraud immediately | Adds latency (~50ms)              |
| Kafka for async      | Decoupled services        | Eventual consistency              |

---

### Question 2: Design a Cross-Border Payment System

**Requirements:**

- Support USD → NGN conversions via stablecoins
- Handle currency exchange rate fluctuations
- Ensure ACID compliance for balance updates
- Provide real-time transaction status
- Scale to 1 million users

**Model Answer:**

#### Architecture

```
User A (USD) ──→ [Conversion Service] ──→ User B (NGN)
                      ↓
                 [Exchange API]
                      ↓
                 [Stablecoin Pool]
```

#### Key Design Points

**1. Saga Pattern for Distributed Transaction:**

```java
@Service
public class CrossBorderPaymentOrchestrator {

    @Transactional
    public PaymentResult initiatePayment(PaymentCommand cmd) {
        // Step 1: Reserve USD from sender
        ReservationResult usdReserve = accountService.reserveFunds(
            cmd.getSenderId(),
            Currency.USD,
            cmd.getAmount()
        );
        if (!usdReserve.isSuccess()) {
            return PaymentResult.failed("Insufficient funds");
        }

        // Step 2: Get exchange quote (external API)
        Quote quote;
        try {
            quote = exchangeService.getQuote(Currency.USD, Currency.NGN, cmd.getAmount());
        } catch (ExchangeUnavailableException e) {
            // Compensate: Release reservation
            accountService.releaseReservation(usdReserve.getReservationId());
            return PaymentResult.failed("Exchange unavailable");
        }

        // Step 3: Execute trade
        TradeResult trade;
        try {
            trade = exchangeService.executeTrade(quote.getQuoteId());
        } catch (TradeExecutionException e) {
            // Compensate: Release reservation
            accountService.releaseReservation(usdReserve.getReservationId());
            return PaymentResult.failed("Trade execution failed");
        }

        // Step 4: Debit USD and Credit NGN (atomic)
        try {
            accountService.debitUsd(cmd.getSenderId(), cmd.getAmount());
            accountService.creditNgn(cmd.getRecipientId(), trade.getConvertedAmount());

            // Commit reservation
            accountService.commitReservation(usdReserve.getReservationId());

            // Publish event
            eventPublisher.publish(new PaymentCompletedEvent(...));

            return PaymentResult.success(trade.getConvertedAmount());
        } catch (Exception e) {
            // This should rarely happen due to reservation
            // But if it does, we need manual intervention
            logger.error("CRITICAL: Payment partially completed", e);
            throw e;
        }
    }
}
```

**2. Handle Exchange Rate Fluctuations:**

```java
@Service
public class ExchangeService {
    private static final Duration QUOTE_VALIDITY = Duration.ofMinutes(2);

    public Quote getQuote(Currency from, Currency to, BigDecimal amount) {
        // Get current rate from external API
        BigDecimal rate = externalApi.getRate(from, to);

        // Add margin for fluctuation risk (e.g., 0.5%)
        BigDecimal margin = new BigDecimal("0.005");
        BigDecimal rateWithMargin = rate.multiply(BigDecimal.ONE.subtract(margin));

        BigDecimal convertedAmount = amount.multiply(rateWithMargin);
        BigDecimal fees = calculateFees(amount);

        Quote quote = new Quote(
            UUID.randomUUID().toString(),
            from,
            to,
            amount,
            rateWithMargin,
            fees,
            convertedAmount.subtract(fees),
            LocalDateTime.now().plus(QUOTE_VALIDITY)
        );

        // Store quote temporarily
        quoteCache.put(quote.getId(), quote, QUOTE_VALIDITY);

        return quote;
    }

    public TradeResult executeTrade(String quoteId) {
        Quote quote = quoteCache.get(quoteId);
        if (quote == null) {
            throw new QuoteExpiredException();
        }

        if (LocalDateTime.now().isAfter(quote.getExpiresAt())) {
            throw new QuoteExpiredException();
        }

        // Execute trade at quoted rate (even if market rate changed)
        return externalApi.executeTrade(quote);
    }
}
```

**3. ACID Compliance with Pessimistic Locking:**

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") Long userId);
}

@Service
public class AccountService {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transferFunds(Long fromUserId, Long toUserId,
                             Currency currency, BigDecimal amount) {
        // Lock both accounts (always in ID order to prevent deadlocks)
        Long firstId = Math.min(fromUserId, toUserId);
        Long secondId = Math.max(fromUserId, toUserId);

        Account first = accountRepo.findByUserIdForUpdate(firstId).orElseThrow();
        Account second = accountRepo.findByUserIdForUpdate(secondId).orElseThrow();

        Account from = (fromUserId.equals(firstId)) ? first : second;
        Account to = (fromUserId.equals(firstId)) ? second : first;

        // Validate balance
        if (from.getBalance(currency).compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        // Update balances atomically
        from.debit(currency, amount);
        to.credit(currency, amount);

        accountRepo.save(from);
        accountRepo.save(to);

        // If any exception occurs, entire transaction rolls back
    }
}
```

---

### Question 3: Design a Real-Time Analytics Dashboard

**Requirements:**

- Show transaction volumes per minute
- Display active merchants count
- Track error rates by service
- P95/P99 latency metrics
- Support 10,000 concurrent dashboard users

**Model Answer:**

#### Architecture

```
Services → Kafka → Stream Processor → Time-Series DB → API → Dashboard
                      (Flink/KStreams)    (InfluxDB/TimescaleDB)
```

#### Implementation

**1. Event Streaming:**

```java
@Service
public class MetricsPublisher {
    @Autowired
    private KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Around("@annotation(Timed)")
    public Object publishMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // Publish success metric
            MetricEvent event = new MetricEvent(
                "method_duration",
                methodName,
                duration,
                "success"
            );
            kafkaTemplate.send("metrics", event);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Publish error metric
            MetricEvent event = new MetricEvent(
                "method_duration",
                methodName,
                duration,
                "error"
            );
            kafkaTemplate.send("metrics", event);

            throw e;
        }
    }
}
```

**2. Stream Processing (Kafka Streams):**

```java
@Component
public class MetricsAggregator {

    @Bean
    public KStream<String, MetricEvent> processMetrics(StreamsBuilder builder) {
        KStream<String, MetricEvent> stream = builder.stream("metrics");

        // Aggregate transactions per minute
        stream
            .filter((key, event) -> event.getType().equals("transaction"))
            .groupBy((key, event) -> event.getServiceName())
            .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
            .count()
            .toStream()
            .to("transaction-counts");

        // Calculate P95 latency
        stream
            .filter((key, event) -> event.getType().equals("method_duration"))
            .groupBy((key, event) -> event.getMethodName())
            .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
            .aggregate(
                () -> new DurationStats(),
                (key, event, stats) -> stats.add(event.getDuration()),
                Materialized.with(Serdes.String(), durationStatsSerde())
            )
            .toStream()
            .mapValues(stats -> stats.getPercentile(95))
            .to("p95-latency");

        return stream;
    }
}
```

**3. Time-Series Database (TimescaleDB):**

```sql
-- Create hypertable for metrics
CREATE TABLE metrics (
    time TIMESTAMPTZ NOT NULL,
    service_name VARCHAR(100),
    metric_name VARCHAR(100),
    value DOUBLE PRECISION,
    tags JSONB
);

-- Convert to hypertable (automatic partitioning by time)
SELECT create_hypertable('metrics', 'time');

-- Create indexes
CREATE INDEX idx_service_name ON metrics (service_name, time DESC);
CREATE INDEX idx_metric_name ON metrics (metric_name, time DESC);

-- Continuous aggregation (pre-compute 1-hour averages)
CREATE MATERIALIZED VIEW metrics_1hour
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    service_name,
    metric_name,
    AVG(value) as avg_value,
    MAX(value) as max_value,
    MIN(value) as min_value
FROM metrics
GROUP BY bucket, service_name, metric_name;

-- Refresh policy (auto-refresh every 5 minutes)
SELECT add_continuous_aggregate_policy('metrics_1hour',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '5 minutes');
```

**4. Caching for Dashboard API:**

```java
@Service
public class DashboardService {
    @Autowired
    private RedisTemplate<String, DashboardData> redis;

    @Cacheable(value = "dashboard", key = "#merchantId", unless = "#result == null")
    public DashboardData getDashboard(String merchantId) {
        // Check cache first (TTL: 10 seconds)
        String cacheKey = "dashboard:" + merchantId;
        DashboardData cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Query time-series DB
        DashboardData data = new DashboardData();
        data.setTransactionVolume(metricsRepo.getTransactionVolume(merchantId, Duration.ofHours(1)));
        data.setErrorRate(metricsRepo.getErrorRate(merchantId, Duration.ofHours(1)));
        data.setP95Latency(metricsRepo.getP95Latency(merchantId, Duration.ofMinutes(5)));

        // Cache for 10 seconds
        redis.opsForValue().set(cacheKey, data, 10, TimeUnit.SECONDS);

        return data;
    }
}
```

---

## Distributed Systems & Microservices

### Question: How do you handle distributed transactions across microservices?

**Answer:**

**1. Avoid Distributed Transactions When Possible:**

- Redesign to keep transaction within single service
- Use eventual consistency where acceptable

**2. Saga Pattern (Choreography):**

```
Payment Service:
  1. Debit account → Publish "AccountDebited" event
     ↓
Order Service (listening):
  2. Create order → Publish "OrderCreated" event
     ↓
Inventory Service (listening):
  3. Reserve items → Publish "ItemsReserved" event
     ↓
Shipping Service (listening):
  4. Create shipment → Publish "ShipmentCreated" event

If any step fails:
  → Publish compensation event
  → Previous services rollback their changes
```

**Implementation:**

```java
// Payment Service
@Service
public class PaymentService {
    @Transactional
    public void processPayment(PaymentCommand cmd) {
        // 1. Debit account in local DB
        accountRepo.debit(cmd.getUserId(), cmd.getAmount());

        // 2. Save event in outbox (same transaction)
        OutboxEvent event = new OutboxEvent(
            "AccountDebited",
            new AccountDebitedPayload(cmd.getUserId(), cmd.getAmount(), cmd.getOrderId())
        );
        outboxRepo.save(event);

        // Transaction commits → both account debit and outbox event saved atomically
    }
}

// Outbox Publisher (separate process)
@Scheduled(fixedDelay = 1000)
public void publishOutboxEvents() {
    List<OutboxEvent> pending = outboxRepo.findUnpublished();
    for (OutboxEvent event : pending) {
        kafkaTemplate.send("order-events", event.getPayload());
        event.setPublished(true);
        outboxRepo.save(event);
    }
}

// Order Service
@Service
public class OrderEventListener {
    @KafkaListener(topics = "order-events")
    @Transactional
    public void handleAccountDebited(AccountDebitedPayload payload) {
        String idempotencyKey = payload.getOrderId() + "-order-created";

        // Idempotent processing
        if (processedEvents.exists(idempotencyKey)) {
            return; // Already processed
        }

        try {
            // Create order
            Order order = new Order(payload.getOrderId(), payload.getUserId(), payload.getAmount());
            orderRepo.save(order);

            // Publish next event
            OutboxEvent event = new OutboxEvent("OrderCreated", new OrderCreatedPayload(order));
            outboxRepo.save(event);

            // Mark as processed
            processedEvents.save(idempotencyKey);
        } catch (Exception e) {
            // Publish compensation event
            OutboxEvent compensate = new OutboxEvent(
                "AccountDebitFailed",
                new RefundPayload(payload.getUserId(), payload.getAmount())
            );
            outboxRepo.save(compensate);
        }
    }
}
```

**3. Two-Phase Commit (2PC) - Use Sparingly:**

Only for critical operations where strong consistency is required.

**Problems with 2PC:**

- Blocking (coordinator failure blocks participants)
- Performance overhead
- Complex error handling

---

### Question: How do you ensure data consistency in eventual consistency systems?

**Answer:**

**1. Idempotent Operations:**

```java
@Service
public class OrderService {
    // Use unique identifiers to detect duplicates
    public void createOrder(CreateOrderCommand cmd) {
        String orderId = cmd.getOrderId(); // Client-generated UUID

        if (orderRepo.existsById(orderId)) {
            return; // Already processed
        }

        Order order = new Order(orderId, cmd.getItems());
        orderRepo.save(order);
    }
}
```

**2. Event Versioning:**

```java
public class PaymentEvent {
    private String eventId; // UUID
    private Long aggregateId; // Payment ID
    private Long version; // Event sequence number
    private EventType type;
    private Object payload;

    // Consumer tracks last processed version
    // Rejects events with version <= last processed
}
```

**3. Reconciliation Jobs:**

```java
@Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
public void reconcileOrders() {
    // Find orders without corresponding payment
    List<Order> orphanedOrders = orderRepo.findWithoutPayment();

    for (Order order : orphanedOrders) {
        Payment payment = paymentService.findByOrderId(order.getId());
        if (payment != null) {
            // Link them
            order.setPaymentId(payment.getId());
            orderRepo.save(order);
        } else if (order.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            // Order older than 24h without payment → cancel
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
        }
    }
}
```

**4. Read-Your-Own-Writes:**

```java
@Service
public class OrderService {
    @Autowired
    private RedisTemplate<String, Order> redis;

    public Order createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderRepo.save(order);

        // Publish event (eventual consistency)
        eventPublisher.publish(new OrderCreatedEvent(order));

        // Write to cache for immediate read
        redis.opsForValue().set("order:" + order.getId(), order, 5, TimeUnit.MINUTES);

        return order;
    }

    public Order getOrder(String orderId) {
        // Try cache first (read your own write)
        Order cached = redis.opsForValue().get("order:" + orderId);
        if (cached != null) {
            return cached;
        }

        // Fallback to DB
        return orderRepo.findById(orderId).orElseThrow();
    }
}
```

---

## Database & Data Management

### Question: How would you design a database schema for 100M users with different account types?

**Answer:**

**1. Table Partitioning:**

```sql
-- Partition users by created_at (monthly partitions)
CREATE TABLE users (
    id BIGSERIAL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    account_type VARCHAR(50),
    ...
) PARTITION BY RANGE (created_at);

CREATE TABLE users_2024_01 PARTITION OF users
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE users_2024_02 PARTITION OF users
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Auto-create partitions
CREATE EXTENSION pg_partman;
SELECT partman.create_parent('public.users', 'created_at', 'native', 'monthly');
```

**2. Sharding Strategy:**

```java
@Configuration
public class ShardingConfiguration {

    @Bean
    public DataSourceRouter dataSourceRouter() {
        Map<Object, Object> shards = new HashMap<>();

        // 10 shards (support 10M users each)
        for (int i = 0; i < 10; i++) {
            DataSource ds = createDataSource("shard-" + i);
            shards.put(i, ds);
        }

        DataSourceRouter router = new DataSourceRouter();
        router.setTargetDataSources(shards);
        return router;
    }

    // Shard selection logic
    public int getShardId(Long userId) {
        return (int) (userId % 10); // Simple modulo sharding
    }
}

@Service
public class UserService {
    public User getUser(Long userId) {
        int shardId = shardingConfig.getShardId(userId);

        // Switch to appropriate shard
        ShardContext.setShardId(shardId);
        try {
            return userRepo.findById(userId).orElseThrow();
        } finally {
            ShardContext.clear();
        }
    }
}
```

**3. Different Account Types (Polymorphic Association):**

```sql
-- Option 1: Single Table Inheritance (STI)
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    account_type VARCHAR(50) NOT NULL, -- 'PERSONAL', 'BUSINESS', 'MERCHANT'

    -- Common fields
    balance DECIMAL(19,4),
    currency VARCHAR(3),
    status VARCHAR(50),

    -- Personal-specific (NULL for business)
    monthly_limit DECIMAL(19,4),

    -- Business-specific (NULL for personal)
    business_name VARCHAR(255),
    tax_id VARCHAR(100),

    -- Merchant-specific
    mcc_code VARCHAR(10),
    settlement_account VARCHAR(100),

    CHECK (
        (account_type = 'PERSONAL' AND monthly_limit IS NOT NULL) OR
        (account_type = 'BUSINESS' AND business_name IS NOT NULL) OR
        (account_type = 'MERCHANT' AND mcc_code IS NOT NULL)
    )
);

CREATE INDEX idx_user_id ON accounts(user_id);
CREATE INDEX idx_account_type ON accounts(account_type);
```

```sql
-- Option 2: Class Table Inheritance (CTI) - Better for distinct types
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    account_type VARCHAR(50) NOT NULL,
    balance DECIMAL(19,4),
    currency VARCHAR(3),
    status VARCHAR(50)
);

CREATE TABLE personal_accounts (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id),
    monthly_limit DECIMAL(19,4)
);

CREATE TABLE business_accounts (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id),
    business_name VARCHAR(255),
    tax_id VARCHAR(100)
);

CREATE TABLE merchant_accounts (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id),
    mcc_code VARCHAR(10),
    settlement_account VARCHAR(100)
);
```

**4. Indexing Strategy:**

```sql
-- Covering index for common query
CREATE INDEX idx_user_status_balance ON accounts(user_id, status) INCLUDE (balance, currency);

-- Partial index for active accounts only
CREATE INDEX idx_active_accounts ON accounts(user_id) WHERE status = 'ACTIVE';

-- Index for range queries
CREATE INDEX idx_balance_currency ON accounts(currency, balance) WHERE status = 'ACTIVE';
```

---

### Question: How do you handle database connection pooling in a high-traffic service?

**Answer:**

**Configuration:**

```yaml
spring:
  datasource:
    hikari:
      # Pool sizing
      maximum-pool-size: 50 # Max connections
      minimum-idle: 10 # Min idle connections

      # Connection lifetime
      max-lifetime: 1800000 # 30 minutes
      idle-timeout: 600000 # 10 minutes

      # Connection acquisition
      connection-timeout: 30000 # 30 seconds

      # Leak detection
      leak-detection-threshold: 60000 # 1 minute

      # Validation
      connection-test-query: SELECT 1
      validation-timeout: 5000
```

**Sizing Formula:**

```
connections = ((core_count * 2) + effective_spindle_count)

For cloud (SSDs):
connections = (core_count * 2) + 1

Example:
- 4 CPU cores → 9 connections per app instance
- 10 app instances → 90 total connections
- Database supports 500 connections → Good (90 < 500)
```

**Monitoring:**

```java
@Component
public class ConnectionPoolMonitor {
    @Autowired
    private HikariDataSource dataSource;

    @Scheduled(fixedRate = 60000) // Every minute
    public void logPoolStats() {
        HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();

        logger.info("Connection Pool Stats:");
        logger.info("Active: {}", poolBean.getActiveConnections());
        logger.info("Idle: {}", poolBean.getIdleConnections());
        logger.info("Waiting: {}", poolBean.getThreadsAwaitingConnection());
        logger.info("Total: {}", poolBean.getTotalConnections());

        // Alert if pool exhausted
        if (poolBean.getThreadsAwaitingConnection() > 0) {
            alertService.send("Connection pool exhausted!");
        }
    }
}
```

---

## Performance & Scalability

### Question: How would you optimize a slow database query?

**Answer:**

**Step 1: Identify the Problem**

```sql
-- Enable query logging
SET log_statement = 'all';
SET log_min_duration_statement = 1000; -- Log queries slower than 1s

-- Use EXPLAIN ANALYZE
EXPLAIN ANALYZE
SELECT u.*, a.balance
FROM users u
JOIN accounts a ON a.user_id = u.id
WHERE u.status = 'ACTIVE'
AND a.currency = 'NGN'
AND a.balance > 1000
ORDER BY u.created_at DESC
LIMIT 100;
```

**Step 2: Common Optimizations**

**1. Add Indexes:**

```sql
-- Before: Sequential scan
EXPLAIN SELECT * FROM transactions WHERE user_id = 123 AND status = 'COMPLETED';
-- Seq Scan on transactions (cost=0.00..10000.00 rows=100)

-- After: Index scan
CREATE INDEX idx_transactions_user_status ON transactions(user_id, status);
-- Index Scan using idx_transactions_user_status (cost=0.43..8.51 rows=100)
```

**2. Use Covering Index:**

```sql
-- Query needs: user_id, status, amount, created_at
-- Bad: Index only on user_id
CREATE INDEX idx_user_id ON transactions(user_id);
-- Still needs to access table for amount, created_at

-- Good: Covering index
CREATE INDEX idx_user_status_covering ON transactions(user_id, status)
INCLUDE (amount, created_at);
-- All data in index, no table access needed
```

**3. Optimize JOIN Order:**

```sql
-- Bad: Cartesian product then filter
SELECT *
FROM users u, accounts a, transactions t
WHERE u.id = a.user_id
AND a.id = t.account_id
AND u.status = 'ACTIVE';

-- Good: Filter early, join later
SELECT *
FROM (SELECT * FROM users WHERE status = 'ACTIVE') u
JOIN accounts a ON a.user_id = u.id
JOIN transactions t ON t.account_id = a.id;
```

**4. Paginate Instead of OFFSET:**

```sql
-- Bad: OFFSET 1000000 (scans and discards 1M rows)
SELECT * FROM transactions
ORDER BY id
LIMIT 100 OFFSET 1000000;

-- Good: Keyset pagination
SELECT * FROM transactions
WHERE id > 1000000  -- Last seen ID
ORDER BY id
LIMIT 100;
```

**5. Denormalize for Read-Heavy Workloads:**

```sql
-- Before: JOIN on every query
SELECT u.name, COUNT(t.id) as transaction_count
FROM users u
LEFT JOIN transactions t ON t.user_id = u.id
GROUP BY u.id, u.name;

-- After: Materialized view
CREATE MATERIALIZED VIEW user_stats AS
SELECT u.id, u.name, COUNT(t.id) as transaction_count
FROM users u
LEFT JOIN transactions t ON t.user_id = u.id
GROUP BY u.id, u.name;

CREATE INDEX idx_user_stats_id ON user_stats(id);

-- Refresh periodically
REFRESH MATERIALIZED VIEW CONCURRENTLY user_stats;
```

**Step 3: Application-Level Caching**

```java
@Service
public class UserService {
    @Cacheable(value = "users", key = "#userId", unless = "#result == null")
    public User getUser(Long userId) {
        return userRepo.findById(userId).orElseThrow();
    }

    @CacheEvict(value = "users", key = "#user.id")
    public void updateUser(User user) {
        userRepo.save(user);
    }
}
```

---

### Question: How do you handle a sudden traffic spike (10x normal load)?

**Answer:**

**1. Auto-Scaling (Horizontal):**

```yaml
# Kubernetes HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100 # Double pods
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50 # Halve pods slowly
          periodSeconds: 60
```

**2. Rate Limiting:**

```java
@Component
public class RateLimiter {
    @Autowired
    private RedisTemplate<String, String> redis;

    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        String script =
            "local current = redis.call('incr', KEYS[1]) " +
            "if current == 1 then " +
            "  redis.call('expire', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

        Long count = redis.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(key),
            "60" // 60 seconds window
        );

        return count <= 100; // 100 requests per minute
    }
}

@RestController
public class PaymentController {
    @PostMapping("/api/v1/payments")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequest request) {
        if (!rateLimiter.allowRequest(request.getUserId())) {
            return ResponseEntity.status(429).body("Rate limit exceeded");
        }

        return ResponseEntity.ok(paymentService.process(request));
    }
}
```

**3. Circuit Breaker:**

```java
@Service
public class ExchangeService {
    private final CircuitBreaker circuitBreaker;

    public ExchangeService() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)              // Open if 50% fail
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(100)
            .minimumNumberOfCalls(10)
            .build();

        this.circuitBreaker = CircuitBreaker.of("exchange-api", config);
    }

    public Quote getQuote(Currency from, Currency to, BigDecimal amount) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                return externalApi.getQuote(from, to, amount);
            } catch (TimeoutException e) {
                // Fallback to cached rate
                return getFallbackQuote(from, to, amount);
            }
        });
    }

    private Quote getFallbackQuote(Currency from, Currency to, BigDecimal amount) {
        BigDecimal cachedRate = rateCache.get(from + "-" + to);
        if (cachedRate == null) {
            throw new ServiceUnavailableException("Exchange API unavailable");
        }

        return new Quote(from, to, amount, cachedRate);
    }
}
```

**4. Queue-Based Load Leveling:**

```java
// Accept requests into queue
@PostMapping("/api/v1/payments")
public ResponseEntity<?> createPayment(@RequestBody PaymentRequest request) {
    String paymentId = UUID.randomUUID().toString();

    // Put in queue (fast)
    kafkaTemplate.send("payment-requests", paymentId, request);

    // Return immediately
    return ResponseEntity.accepted()
        .body(new PaymentResponse(paymentId, PaymentStatus.PENDING));
}

// Process from queue at controlled rate
@KafkaListener(topics = "payment-requests", concurrency = "10")
public void processPayment(PaymentRequest request) {
    try {
        PaymentResult result = paymentService.process(request);

        // Notify user
        notificationService.send(request.getUserId(), result);
    } catch (Exception e) {
        // Send to DLQ
        dlqProducer.send("payment-dlq", request);
    }
}
```

**5. Database Read Replicas:**

```java
@Configuration
public class DatabaseConfiguration {

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://primary-db:5432/payment_db")
            .build();
    }

    @Bean
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://replica-db:5432/payment_db")
            .build();
    }

    @Bean
    public DataSource routingDataSource() {
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("primary", primaryDataSource());
        dataSourceMap.put("replica", replicaDataSource());

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        return routingDataSource;
    }
}

// Use replica for read-only queries
@Transactional(readOnly = true)
public List<Payment> getRecentPayments(Long userId) {
    DataSourceContext.set("replica");
    try {
        return paymentRepo.findByUserIdOrderByCreatedAtDesc(userId);
    } finally {
        DataSourceContext.clear();
    }
}
```

---

## Code Design & Patterns

### Question: Explain your approach to writing testable code.

**Answer:**

**1. Dependency Injection:**

```java
// Bad: Hard to test (can't mock EmailSender)
public class OrderService {
    private EmailSender emailSender = new EmailSender();

    public void createOrder(Order order) {
        // ... create order
        emailSender.send(order.getUserEmail(), "Order created");
    }
}

// Good: Testable (can inject mock)
public class OrderService {
    private final EmailSender emailSender;

    @Autowired
    public OrderService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void createOrder(Order order) {
        // ... create order
        emailSender.send(order.getUserEmail(), "Order created");
    }
}

// Test
@Test
public void testCreateOrder() {
    EmailSender mockEmailSender = mock(EmailSender.class);
    OrderService service = new OrderService(mockEmailSender);

    service.createOrder(new Order(...));

    verify(mockEmailSender).send(anyString(), eq("Order created"));
}
```

**2. Interface Segregation:**

```java
// Repository interface (easy to mock)
public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String id);
}

// Real implementation
@Repository
public class JpaPaymentRepository implements PaymentRepository {
    @Autowired
    private JpaPaymentDao dao;

    @Override
    public Payment save(Payment payment) {
        return dao.save(payment);
    }

    @Override
    public Optional<Payment> findById(String id) {
        return dao.findById(id);
    }
}

// Test with fake
public class FakePaymentRepository implements PaymentRepository {
    private Map<String, Payment> storage = new HashMap<>();

    @Override
    public Payment save(Payment payment) {
        storage.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }
}

@Test
public void testPaymentService() {
    PaymentRepository fakeRepo = new FakePaymentRepository();
    PaymentService service = new PaymentService(fakeRepo);

    // Test without real database
    Payment payment = service.createPayment(...);
    assertNotNull(payment.getId());
}
```

**3. Test Data Builders:**

```java
public class PaymentBuilder {
    private String id = UUID.randomUUID().toString();
    private BigDecimal amount = new BigDecimal("100.00");
    private Currency currency = Currency.USD;
    private PaymentStatus status = PaymentStatus.PENDING;

    public static PaymentBuilder aPayment() {
        return new PaymentBuilder();
    }

    public PaymentBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public PaymentBuilder withStatus(PaymentStatus status) {
        this.status = status;
        return this;
    }

    public Payment build() {
        return new Payment(id, amount, currency, status);
    }
}

// Usage in tests
@Test
public void testLargePayment() {
    Payment payment = aPayment()
        .withAmount(new BigDecimal("50000.00"))
        .withStatus(PaymentStatus.REVIEW_REQUIRED)
        .build();

    FraudCheckResult result = fraudService.check(payment);
    assertEquals(FraudCheckResult.REVIEW, result.getDecision());
}
```

---

## Behavioral Questions

### Question: Tell me about a time you optimized a system for better performance.

**Example Answer:**

"At my previous company, we had a payment processing service handling 500 transactions per second. During peak hours, response times spiked to 3-5 seconds, and we were getting timeout errors.

**Investigation:**

- Added APM (Application Performance Monitoring) with New Relic
- Discovered 80% of time spent in database queries
- Found N+1 query problem: fetching user, then account, then transaction history in separate queries

**Solution:**

1. **Optimized Queries:**
   - Changed to use JPA fetch joins: `@EntityGraph(attributePaths = {"account", "transactions"})`
   - Reduced 100+ queries per request to 1 query
2. **Added Caching:**
   - Cached user and account data in Redis (TTL: 5 minutes)
   - Cache hit rate: 85%
3. **Database Indexing:**
   - Added covering index on `(user_id, status)` for common query
   - Query time: 200ms → 15ms

**Results:**

- Response time: 3-5s → 150ms (95th percentile)
- Throughput: 500 TPS → 2000 TPS
- CPU usage: 80% → 30%
- Zero timeout errors

**Learning:** Always measure before optimizing. The real bottleneck (N+1 queries) wasn't what we initially suspected (application logic)."

---

### Question: Describe a challenging bug you debugged.

**Example Answer:**

"We had intermittent payment failures (~1% of transactions) that we couldn't reproduce in testing.

**Investigation:**

1. **Gathered Data:**

   - Added correlation IDs to trace requests across services
   - Increased logging verbosity
   - Set up alerts for failed payments

2. **Pattern Found:**
   - Failures only during high traffic (>1000 TPS)
   - Always same error: `OptimisticLockException`
   - Multiple payment attempts for same transaction

**Root Cause:**

- Payment button didn't disable after click
- Users clicked multiple times during network lag
- Multiple concurrent requests with same transaction ID
- Optimistic locking prevented double-charge BUT failed the transaction

**Solution:**

1. **Frontend:** Disable button after click
2. **Backend:** Implement idempotency with dedicated table
3. **Return cached response** if duplicate detected

```java
@Transactional
public PaymentResponse processPayment(PaymentRequest request) {
    String idempotencyKey = request.getIdempotencyKey();

    // Check if already processed
    Optional<IdempotencyRecord> existing = idempotencyRepo.findByKey(idempotencyKey);
    if (existing.isPresent()) {
        return existing.get().getResponse(); // Return cached response
    }

    // Process payment...
}
```

**Results:**

- Failure rate: 1% → 0.01%
- Better user experience (no retry needed)

**Learning:** Distributed systems need idempotency. Can't rely on client behavior."

---

## Questions to Ask

**About the Role:**

1. "What's the current scale of the payment processing system? (TPS, daily volume)"
2. "What are the biggest technical challenges the team is facing right now?"
3. "How does the team balance feature development vs technical debt?"
4. "What's the on-call rotation like?"

**About Technology:**

1. "What's the migration strategy from monolith to microservices?" (if applicable)
2. "How do you handle database schema migrations in production?"
3. "What's your approach to testing (unit, integration, e2e)?"
4. "How do you monitor and alert on system health?"

**About Growth:**

1. "What opportunities are there for learning and professional development?"
2. "How does the team stay current with new technologies?"
3. "What does success look like in this role after 6 months?"

---

## Summary Checklist

**System Design:**

- [ ] Understand requirements (functional & non-functional)
- [ ] Discuss trade-offs explicitly
- [ ] Consider scalability from the start
- [ ] Plan for failure scenarios
- [ ] Explain data consistency strategy

**Coding:**

- [ ] Write clean, readable code
- [ ] Use meaningful variable names
- [ ] Add comments for complex logic
- [ ] Consider edge cases
- [ ] Discuss time/space complexity

**Communication:**

- [ ] Think out loud
- [ ] Ask clarifying questions
- [ ] Explain your reasoning
- [ ] Be open to feedback
- [ ] Admit what you don't know

**Moniepoint-Specific:**

- [ ] Emphasize payment system experience
- [ ] Discuss high-throughput scenarios
- [ ] Show understanding of financial consistency
- [ ] Mention AWS/Kubernetes experience
- [ ] Demonstrate problem-solving at scale

Good luck with your interview! 🚀
