# CQRS (Command Query Responsibility Segregation)

**A comprehensive guide to separating reads and writes for scalable systems**

---

## Table of Contents

1. [What is CQRS?](#what-is-cqrs)
2. [Why Use CQRS?](#why-use-cqrs)
3. [CQRS vs Traditional Architecture](#cqrs-vs-traditional-architecture)
4. [Implementation Patterns](#implementation-patterns)
5. [CQRS + Event Sourcing](#cqrs--event-sourcing)
6. [Trade-offs](#trade-offs)

---

## What is CQRS?

**CQRS** = **C**ommand **Q**uery **R**esponsibility **S**egregation

**Core Idea:** Separate the **write** model (commands) from the **read** model (queries).

### Traditional Approach

```
┌────────────┐
│   Client   │
└──────┬─────┘
       │
   ┌───┴────────────────┐
   │  Same Model for    │
   │  Reads & Writes    │
   └───┬────────────────┘
       │
   ┌───▼────┐
   │   DB   │
   └────────┘
```

### CQRS Approach

```
┌────────────┐
│   Client   │
└──┬──────┬──┘
   │      │
Commands   Queries
   │      │
   ▼      ▼
┌──────┐ ┌──────────┐
│Write │ │   Read   │
│Model │ │  Model   │
└──┬───┘ └────┬─────┘
   │          │
   ▼          ▼
┌─────┐   ┌───────┐
│Write│   │ Read  │
│ DB  │───│  DB   │ (Synchronized)
└─────┘   └───────┘
```

**Key Points:**
- **Commands**: Change state (create, update, delete)
- **Queries**: Read state (no side effects)
- **Separate models**: Optimized for different purposes

---

## Why Use CQRS?

### Problem 1: Read/Write Asymmetry

**Typical E-Commerce System:**
- **Writes**: 100 orders/second
- **Reads**: 10,000 product views/second

**Solution:** Optimize read and write paths independently.

### Problem 2: Complex Queries on Write-Optimized Model

```java
// Write model: Normalized (3NF) for consistency
CREATE TABLE orders (id, user_id, status);
CREATE TABLE order_items (id, order_id, product_id, quantity);
CREATE TABLE products (id, name, price);

// Complex query requires 3 joins
SELECT o.*, oi.*, p.*
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
WHERE o.user_id = ?;
```

```java
// Read model: Denormalized for fast queries
CREATE MATERIALIZED VIEW order_summaries AS
SELECT 
    o.id,
    o.user_id,
    o.status,
    JSON_AGG(JSON_BUILD_OBJECT(
        'product_name', p.name,
        'quantity', oi.quantity,
        'price', p.price
    )) as items,
    SUM(p.price * oi.quantity) as total
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
GROUP BY o.id;

-- Now just:
SELECT * FROM order_summaries WHERE user_id = ?;  -- Fast!
```

### Problem 3: Different Consistency Requirements

- **Writes**: Need ACID guarantees (strong consistency)
- **Reads**: Can tolerate eventual consistency

---

## CQRS vs Traditional Architecture

### Traditional (Single Model)

```java
@Entity
public class Order {
    @Id
    private Long id;
    
    @ManyToOne
    private User user;
    
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
    
    private OrderStatus status;
    
    // Used for both reads and writes
}

@Service
public class OrderService {
    public void createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderRepo.save(order);  // Write
    }
    
    public Order getOrder(Long id) {
        return orderRepo.findById(id);  // Read (same model)
    }
    
    public List<Order> getUserOrders(Long userId) {
        return orderRepo.findByUserId(userId);  // Read (same model)
    }
}
```

**Problems:**
- N+1 queries for lazy-loaded associations
- Complex joins for simple reads
- Write model constrained by read requirements

### CQRS (Separate Models)

```java
// ═══════════════════════════════════════════════════════════
// WRITE SIDE (Commands)
// ═══════════════════════════════════════════════════════════
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @Version
    private Long version;  // Optimistic locking
    
    // No associations - simple write model
}

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    private Long id;
    
    @Column(name = "order_id")
    private Long orderId;
    
    @Column(name = "product_id")
    private Long productId;
    
    private Integer quantity;
}

@Service
public class OrderCommandService {
    @Transactional
    public void createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd.getUserId());
        orderRepo.save(order);
        
        for (OrderItemDto item : cmd.getItems()) {
            OrderItem orderItem = new OrderItem(order.getId(), item);
            orderItemRepo.save(orderItem);
        }
        
        // Publish event
        eventPublisher.publish(new OrderCreatedEvent(order.getId()));
    }
}

// ═══════════════════════════════════════════════════════════
// READ SIDE (Queries)
// ═══════════════════════════════════════════════════════════
@Table(name = "order_summaries")  // Materialized view
public class OrderSummary {
    @Id
    private Long orderId;
    
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Integer itemCount;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private List<OrderItemSummary> items;  // Denormalized
    
    private LocalDateTime createdAt;
    
    // Optimized for queries
}

@Service
public class OrderQueryService {
    public OrderSummary getOrder(Long orderId) {
        return orderSummaryRepo.findById(orderId).orElseThrow();
        // Fast - no joins!
    }
    
    public List<OrderSummary> getUserOrders(Long userId) {
        return orderSummaryRepo.findByUserId(userId);
        // Fast - denormalized!
    }
}
```

---

## Implementation Patterns

### Pattern 1: Same Database, Different Tables

```
┌─────────────────┐
│    Write DB     │
├─────────────────┤
│ orders          │
│ order_items     │
│ products        │
└─────────────────┘
        │
        │ Trigger/Event
        ▼
┌─────────────────┐
│    Read DB      │
├─────────────────┤
│ order_summaries │ (Denormalized)
└─────────────────┘
```

**Implementation:**

```sql
-- Write tables (normalized)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(50),
    created_at TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id),
    product_id BIGINT,
    quantity INT,
    price DECIMAL(19,4)
);

-- Read view (denormalized)
CREATE MATERIALIZED VIEW order_summaries AS
SELECT 
    o.id as order_id,
    o.user_id,
    o.status,
    o.created_at,
    COUNT(oi.id) as item_count,
    SUM(oi.price * oi.quantity) as total_amount,
    JSON_AGG(
        JSON_BUILD_OBJECT(
            'product_id', oi.product_id,
            'quantity', oi.quantity,
            'price', oi.price
        )
    ) as items
FROM orders o
LEFT JOIN order_items oi ON oi.order_id = o.id
GROUP BY o.id;

CREATE INDEX idx_order_summaries_user ON order_summaries(user_id);

-- Refresh strategy
REFRESH MATERIALIZED VIEW CONCURRENTLY order_summaries;
```

### Pattern 2: Separate Databases

```
Commands ──→ ┌──────────┐
             │PostgreSQL│ (Write)
             │ (Master) │
             └────┬─────┘
                  │
            Event Stream
                  │ (Kafka)
                  ▼
Queries ───→ ┌──────────────┐
             │Elasticsearch │ (Read)
             │(Optimized for│
             │ Full-Text)   │
             └──────────────┘
```

**Implementation:**

```java
// Write side
@Service
public class OrderCommandService {
    @Autowired
    private OrderRepository orderRepo;
    
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafka;
    
    @Transactional
    public void createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderRepo.save(order);
        
        // Publish to Kafka
        OrderCreatedEvent event = new OrderCreatedEvent(order);
        kafka.send("order-events", event);
    }
}

// Read side (Elasticsearch)
@Document(indexName = "orders")
public class OrderDocument {
    @Id
    private String id;
    
    @Field(type = FieldType.Long)
    private Long userId;
    
    @Field(type = FieldType.Text)
    private String status;
    
    @Field(type = FieldType.Nested)
    private List<OrderItemDocument> items;
    
    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;
    
    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}

@Service
public class OrderProjectionService {
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    
    @KafkaListener(topics = "order-events")
    public void projectOrder(OrderCreatedEvent event) {
        OrderDocument doc = new OrderDocument();
        doc.setId(event.getOrderId().toString());
        doc.setUserId(event.getUserId());
        doc.setStatus(event.getStatus());
        doc.setItems(event.getItems());
        doc.setTotalAmount(event.getTotalAmount());
        doc.setCreatedAt(event.getCreatedAt());
        
        elasticsearchTemplate.save(doc);
    }
}

// Query service
@Service
public class OrderQueryService {
    @Autowired
    private ElasticsearchTemplate es;
    
    public List<OrderDocument> searchOrders(String query) {
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.multiMatchQuery(query, "status", "items.productName"))
            .build();
        
        SearchHits<OrderDocument> hits = es.search(searchQuery, OrderDocument.class);
        return hits.stream()
            .map(SearchHit::getContent)
            .collect(Collectors.toList());
    }
}
```

---

## CQRS + Event Sourcing

**Perfect Combination:**
- Event Sourcing provides the events
- CQRS uses events to build read models

```
Command ──→ Aggregate ──→ Events ──→ Event Store
                                          │
                                          ├──→ Projection 1 (SQL)
                                          ├──→ Projection 2 (Elasticsearch)
                                          └──→ Projection 3 (Redis Cache)
```

**Example:**

```java
// Command side (Event Sourcing)
@Service
public class OrderCommandService {
    @Autowired
    private EventStore eventStore;
    
    @Transactional
    public void createOrder(CreateOrderCommand cmd) {
        // Load aggregate from events
        Order order = new Order(cmd.getUserId());
        
        // Execute command (generates events)
        order.addItem(cmd.getProduct(), cmd.getQuantity());
        order.confirm();
        
        // Save events
        List<DomainEvent> events = order.getUncommittedEvents();
        eventStore.append(order.getId(), events);
        
        // Publish events
        events.forEach(event -> kafka.send("order-events", event));
    }
}

// Read side (Multiple Projections)
@Component
public class OrderProjections {
    
    // Projection 1: SQL for transactional queries
    @KafkaListener(topics = "order-events", groupId = "sql-projection")
    public void updateSqlProjection(OrderCreatedEvent event) {
        jdbcTemplate.update(
            "INSERT INTO order_summaries (order_id, user_id, status, total_amount) VALUES (?, ?, ?, ?)",
            event.getOrderId(), event.getUserId(), event.getStatus(), event.getTotalAmount()
        );
    }
    
    //Projection 2: Elasticsearch for search
    @KafkaListener(topics = "order-events", groupId = "es-projection")
    public void updateElasticsearchProjection(OrderCreatedEvent event) {
        OrderDocument doc = new OrderDocument(event);
        elasticsearchTemplate.save(doc);
    }
    
    // Projection 3: Redis for real-time dashboards
    @KafkaListener(topics = "order-events", groupId = "redis-projection")
    public void updateRedisProjection(OrderCreatedEvent event) {
        String key = "user:" + event.getUserId() + ":order_count";
        redis.increment(key);
    }
}
```

---

## Trade-offs

### Pros ✅

**1. Scalability:**
- Scale reads and writes independently
- Read replicas for queries
- Write throughput not affected by reads

**2. Performance:**
- Denormalized read models = Fast queries
- No complex joins
- Can use different storage (Elasticsearch, Redis)

**3. Flexibility:**
- Multiple read models for different use cases
- Can change read model without touching write model

**4. Availability:**
- Read side can be eventual consistent
- Writes don't block reads

### Cons ❌

**1. Complexity:**
- Two models to maintain
- Synchronization logic
- Eventual consistency

**2. Eventual Consistency:**
- Read model may lag behind
- Users see stale data

**3. Code Duplication:**
- Same data in multiple places
- Need to keep in sync

**4. Learning Curve:**
- Team needs to understand pattern
- Debugging is harder

---

## When to Use CQRS

### Good Use Cases ✅

**1. Read/Write Asymmetry:**
- E-commerce (many reads, few writes)
- Analytics dashboards
- Reporting systems

**2. Complex Queries:**
- Need different representations for different use cases
- Full-text search
- Complex aggregations

**3. Scalability Requirements:**
- Need to scale reads and writes independently
- High traffic systems

**4. Event-Driven Systems:**
- Already using event sourcing
- Microservices architecture

### Not Suitable ❌

**1. Simple CRUD:**
- Basic applications
- Low traffic

**2. Strong Consistency Required:**
- Real-time inventory
- Stock trading (unless carefully designed)

**3. Small Teams:**
- Added complexity not worth it
- Maintenance burden

---

## Summary

**CQRS:**
✅ Separate read and write models  
✅ Optimize each side independently  
✅ Perfect for read-heavy systems  
✅ Pairs well with Event Sourcing

**Core Pattern:**
```
Commands → Write Model → Events → Read Models
                                     ├→ SQL
                                     ├→ Elasticsearch
                                     └→ Cache
```

**Key Benefits:**
- **Scalability**: Scale reads and writes separately
- **Performance**: Denormalized read models
- **Flexibility**: Multiple read models

**Challenges:**
- **Complexity**: Two models to manage
- **Eventual Consistency**: Reads may lag
- **Synchronization**: Keep models in sync

**Decision Matrix:**

| Factor | Traditional | CQRS |
|--------|------------|------|
| Read/Write Ratio | 1:1 | 100:1 |
| Consistency | Strong | Eventual |
| Complexity | Low | High |
| Scalability | Vertical | Horizontal |
| Query Patterns | Simple | Complex |

**Next Steps:**
- Read [event-sourcing.md](event-sourcing.md) for event-driven architecture
- Study our payment system's CQRS implementation
- Practice designing read models for your domain
