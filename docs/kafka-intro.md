# Apache Kafka Introduction

Welcome to the world of event-driven architecture! This guide explains Apache Kafka, a distributed event streaming platform that powers our payment system's asynchronous communication.

## Table of Contents

1. [What is Apache Kafka?](#what-is-apache-kafka)
2. [Core Concepts](#core-concepts)
3. [Why Use Kafka?](#why-use-kafka)
4. [Kafka vs Alternatives](#kafka-vs-alternatives)
5. [How Kafka Works](#how-kafka-works)
6. [Spring Kafka Basics](#spring-kafka-basics)
7. [Best Practices](#best-practices)

---

## What is Apache Kafka?

**Apache Kafka** is a distributed event streaming platform originally developed by LinkedIn and now maintained by Apache Software Foundation.

Think of Kafka as a **super-powered message queue** or **event log** that:

- Stores streams of records (events/messages)
- Allows applications to publish (produce) events
- Allows applications to subscribe (consume) events
- Provides durability, scalability, and fault-tolerance

### Real-World Analogy

Imagine a **newspaper distribution system**:

- **Publishers** (payment-service) write articles
- **Newspaper** (Kafka topic) stores articles
- **Subscribers** (ledger-service) read articles they care about
- Articles are **permanently archived** for a retention period
- Multiple newspapers exist for different topics (sports, business, tech)

---

## Core Concepts

### 1. Topic

A **topic** is a category or stream name where events are published.

```
Topics are like:
- Database tables (but append-only)
- Log files (but distributed)
- Channels in Slack
- Queues in RabbitMQ

Examples:
- payment-events
- user-registrations
- order-confirmations
```

**Key properties:**

- Named stream of events
- Append-only log
- Can have multiple publishers/subscribers
- Divided into partitions for scalability

### 2. Partition

A topic is divided into **partitions** for parallelism and scalability.

```
Topic: payment-events
├─ Partition 0: [Event1, Event4, Event7, ...]
├─ Partition 1: [Event2, Event5, Event8, ...]
└─ Partition 2: [Event3, Event6, Event9, ...]
```

**Why partitions?**

- **Scalability**: Different partitions can be on different servers
- **Parallelism**: Multiple consumers can read different partitions
- **Ordering**: Messages within a partition are ordered

**How events are assigned to partitions:**

1. **With key**: Hash(key) % num_partitions
2. **Without key**: Round-robin

Example:

```java
// Same paymentId always goes to same partition
kafkaTemplate.send("payment-events", paymentId.toString(), eventJson);
```

### 3. Producer

A **producer** is an application that publishes (sends) events to Kafka.

```java
// Our payment-service is a producer
@Service
public class OutboxPublisher {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void publishEvent(PaymentEvent event) {
        kafkaTemplate.send("payment-events", event.getPaymentId(), eventJson);
    }
}
```

**Producer responsibilities:**

- Serialize data to bytes
- Choose which partition to send to
- Handle send failures and retries
- Buffer and batch messages for efficiency

### 4. Consumer

A **consumer** is an application that subscribes to topics and processes events.

```java
// Our ledger-service is a consumer
@Component
public class PaymentEventListener {
    @KafkaListener(topics = "payment-events", groupId = "ledger-service")
    public void handleEvent(String eventJson) {
        // Process event
    }
}
```

**Consumer responsibilities:**

- Deserialize data from bytes
- Track which messages have been processed (offsets)
- Handle processing failures
- Commit offsets when done

### 5. Consumer Group

A **consumer group** is a set of consumers that cooperate to consume a topic.

```
Topic with 3 partitions:
Partition 0 → Consumer A (group: ledger-service)
Partition 1 → Consumer B (group: ledger-service)  ← Load balanced!
Partition 2 → Consumer C (group: ledger-service)

If Consumer A dies:
Partition 0 → Consumer B (rebalanced)
Partition 1 → Consumer B
Partition 2 → Consumer C
```

**Rules:**

- Each partition consumed by exactly ONE consumer in a group
- Different groups can consume the same partitions
- Enables horizontal scaling

### 6. Offset

An **offset** is a position in the partition - which messages you've read.

```
Partition 0: [Msg0, Msg1, Msg2, Msg3, Msg4, Msg5, ...]
                             ↑
                        Offset = 2 (read up to Msg2)
```

**Offset tracking:**

- Kafka stores offsets per consumer group
- Consumer commits offset after processing
- On restart, consumer resumes from last committed offset
- Enables exactly-once or at-least-once semantics

---

## Why Use Kafka?

### 1. Decoupling Services

**Without Kafka (Synchronous):**

```
Payment Service → (HTTP call) → Ledger Service
                ↓
          If ledger is down, payment fails!
```

**With Kafka (Asynchronous):**

```
Payment Service → Kafka → Ledger Service
                   ↓
            Events buffered if ledger is down
```

### 2. Scalability

- Add more partitions → Handle more events
- Add more consumers → Process faster
- Add more brokers → Store more data

### 3. Durability

- Events are written to disk
- Replicated across multiple servers
- Survive server failures
- Can replay events from any point

### 4. Pub/Sub Model

- One producer, many consumers
- Add new consumers without changing producers
- Each consumer processes independently

Example:

```
payment-service publishes PaymentEvent
    ├─ ledger-service → Creates ledger entry
    ├─ notification-service → Sends email
    ├─ analytics-service → Updates dashboards
    └─ audit-service → Logs for compliance
```

### 5. Event Sourcing

- Store complete history of events
- Rebuild state from events
- Audit trail for free
- Time travel debugging

---

## Kafka vs Alternatives

### Kafka vs RabbitMQ

| Feature               | Kafka                            | RabbitMQ                  |
| --------------------- | -------------------------------- | ------------------------- |
| **Model**             | Log-based publish/subscribe      | Traditional message queue |
| **Message retention** | Days/weeks (configurable)        | Until consumed            |
| **Throughput**        | Very high (millions/sec)         | Moderate (thousands/sec)  |
| **Ordering**          | Within partition                 | Within queue              |
| **Replay**            | Yes (from any offset)            | No                        |
| **Use case**          | Event streaming, logs, analytics | Task queues, RPC patterns |

**When to use Kafka:**

- High throughput needed
- Event history matters
- Multiple consumers need same data
- Stream processing

**When to use RabbitMQ:**

- Traditional request/response
- Complex routing needs
- Task distribution
- Lower throughput

### Kafka vs AWS SQS/SNS

| Feature       | Kafka                     | SQS/SNS              |
| ------------- | ------------------------- | -------------------- |
| **Hosting**   | Self-hosted or MSK        | Fully managed by AWS |
| **Cost**      | Server costs              | Per-message pricing  |
| **Ordering**  | Guaranteed per partition  | FIFO queues only     |
| **Retention** | Configurable (days/weeks) | SQS: 14 days max     |
| **Replay**    | Yes                       | No                   |

**When to use Kafka:**

- Need message replay
- High message volume (cost-effective)
- Strong ordering guarantees
- Already on-prem or multi-cloud

**When to use SQS/SNS:**

- AWS ecosystem
- Don't want to manage infrastructure
- Lower message volume
- Simple use cases

---

## How Kafka Works

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Kafka Cluster                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐       │
│  │  Broker 1  │  │  Broker 2  │  │  Broker 3  │       │
│  │            │  │            │  │            │       │
│  │ Topic A    │  │ Topic A    │  │ Topic A    │       │
│  │ Partition0 │  │ Partition1 │  │ Partition2 │       │
│  │            │  │            │  │            │       │
│  └────────────┘  └────────────┘  └────────────┘       │
└─────────────────────────────────────────────────────────┘
         ↑                                    ↓
    Producers                            Consumers
```

### Message Flow

1. **Producer** creates message
2. **Serializer** converts to bytes
3. **Partitioner** chooses partition (based on key)
4. **Kafka Broker** writes to partition log
5. **Replication** copies to other brokers (for fault tolerance)
6. **Acknowledgment** sent back to producer
7. **Consumer** polls for new messages
8. **Deserializer** converts bytes back to object
9. **Processing** happens in consumer
10. **Offset commit** marks message as processed

### Guarantees

**At-least-once delivery** (default):

- Message delivered one or more times
- Producer retries on failure
- Consumer may receive duplicates
- **You must handle duplicates!** (idempotency)

**Exactly-once** (complex):

- Requires transactions
- Performance overhead
- Not always necessary

---

## Spring Kafka Basics

### Producer Configuration

```java
@Configuration
public class KafkaProducerConfig {
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### Sending Messages

```java
@Service
public class EventPublisher {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendEvent(String topic, String key, String value) {
        kafkaTemplate.send(topic, key, value);
    }
}
```

### Consumer Configuration

```java
@Configuration
public class KafkaConsumerConfig {
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "my-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
```

### Consuming Messages

```java
@Component
public class EventListener {
    @KafkaListener(topics = "payment-events", groupId = "ledger-service")
    public void handleEvent(String message) {
        // Process message
        System.out.println("Received: " + message);
    }
}
```

---

## Best Practices

### 1. Message Design

✅ **Include all necessary data** in the event

```java
// Good - self-contained
{
    "paymentId": 123,
    "userId": 456,
    "amount": 100.00,
    "currency": "USD",
    "timestamp": "2024-12-02T10:00:00Z"
}
```

❌ **Don't require lookups**

```java
// Bad - forces consumer to call API
{
    "paymentId": 123
    // Consumer must fetch details from payment-service
}
```

### 2. Idempotency

**Always design consumers to be idempotent!**

Kafka delivers "at-least-once", so handle duplicates:

```java
@KafkaListener(topics = "payment-events")
public void handle(PaymentEvent event, @Header(KafkaHeaders.OFFSET) long offset) {
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

### 3. Error Handling

```java
@KafkaListener(topics = "payment-events")
public void handle(String message) {
    try {
        processMessage(message);
    } catch (RecoverableException e) {
        // Throw to retry (don't commit offset)
        throw e;
    } catch (UnrecoverableException e) {
        // Log and skip (commit offset to move on)
        logger.error("Skipping bad message", e);
    }
}
```

### 4. Monitoring

Track these metrics:

- **Lag**: How far behind consumers are
- **Throughput**: Messages per second
- **Error rate**: Failed message processing
- **Partition count**: For scaling

---

## Summary

Kafka is a powerful tool for building event-driven systems:

✅ **Decouples** services through asynchronous messaging  
✅ **Scales** horizontally with partitions and consumers  
✅ **Persists** events for replay and auditing  
✅ **Provides** ordering within partitions  
✅ **Enables** real-time stream processing

**Remember:**

- Topics organize events by category
- Partitions enable parallelism and ordering
- Consumers track progress with offsets
- Always handle duplicates (idempotency!)
- Use keys to maintain per-entity ordering

**Next Steps:**

- Read [outbox-pattern.md](outbox-pattern.md) to see how we guarantee message delivery
- Read [saga-pattern.md](saga-pattern.md) for distributed transactions
- Explore Kafka UI at http://localhost:8090 when running docker-compose

Happy streaming! 🚀
