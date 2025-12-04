# Scaling Strategy - From 1 to 1,000,000 Users

A practical guide to scaling a payment system from prototype to production, handling millions of users.

## Table of Contents

1. [The Scaling Journey](#the-scaling-journey)
2. [Horizontal vs Vertical Scaling](#horizontal-vs-vertical-scaling)
3. [Stateless Service Design](#stateless-service-design)
4. [Database Scaling](#database-scaling)
5. [Caching Strategies](#caching-strategies)
6. [Load Balancing](#load-balancing)
7. [Scaling Kafka](#scaling-kafka)
8. [Path to 1M Users](#path-to-1m-users)

---

## The Scaling Journey

### Stage 1: MVP (1-100 users)

```
┌──────────────────────────────────┐
│    Single Server                 │
│  ┌────────────┐  ┌──────────┐   │
│  │ Spring Boot│  │PostgreSQL│   │
│  │ App        │  │          │   │
│  └────────────┘  └──────────┘   │
└──────────────────────────────────┘

Cost: $50/month
Response time: <50ms
Uptime: 95%
```

**Good enough for:**

- MVP/Prototype
- Internal tools
- Small businesses

**Problems:**

- Single point of failure
- Limited capacity (~100 concurrent users)
- Can't handle traffic spikes

### Stage 2: Growing (100-10K users)

```
        ┌──────────────┐
        │Load Balancer │
        └───────┬──────┘
                │
        ┌───────┴────────┐
        │                │
    ┌───▼───┐       ┌───▼───┐
    │ App 1 │       │ App 2 │
    └───┬───┘       └───┬───┘
        │               │
        └───────┬───────┘
                │
          ┌─────▼─────┐
          │ PostgreSQL│
          │ (Primary) │
          └───────────┘

Cost: $500/month
Response time: <100ms
Uptime: 99%
```

**Changes:**

- 2 application servers (horizontal scaling)
- Load balancer distributes traffic
- Still single database (bottleneck)

### Stage 3: Scale (10K-100K users)

```
        ┌──────────────┐
        │Load Balancer │
        └───────┬──────┘
                │
    ┌───────────┼───────────┐
    │           │           │
┌───▼───┐   ┌──▼────┐  ┌──▼────┐
│ App 1 │   │ App 2 │  │ App 3 │
└───┬───┘   └───┬───┘  └───┬───┘
    │           │          │
    └───────────┼──────────┘
                │
    ┌───────────┴────────────┐
    │                        │
┌───▼──────────┐      ┌──────▼──────┐
│ Redis Cache  │      │  PostgreSQL │
└──────────────┘      │  - Primary  │
                      │  - Read     │
                      │    Replicas │
                      └─────────────┘

Cost: $3,000/month
Response time: <50ms (with cache)
Uptime: 99.9%
```

**Changes:**

- 3+ application servers
- Redis cache reduces database load
- PostgreSQL read replicas for queries
- Write still goes to primary (bottleneck)

### Stage 4: Enterprise (100K-1M+ users)

```
        ┌──────────────┐         ┌──────────────┐
        │  CDN         │         │  Kafka       │
        └──────────────┘         │  Cluster     │
                │                └──────────────┘
        ┌───────▼────────┐              │
        │  Load Balancer │              │
        │  (Multi-Region)│              │
        └───────┬────────┘              │
                │                       │
    ┌───────────┼───────────────┐       │
    │           │               │       │
┌───▼───┐   ┌──▼────┐  ... ┌──▼────┐  │
│ App 1 │   │ App 2 │      │ App N │  │
└───┬───┘   └───┬───┘      └───┬───┘  │
    │           │              │       │
    └───────────┼──────────────┘       │
                │                      │
    ┌───────────┴────────────┐        │
    │                        │        │
┌───▼──────────┐      ┌──────▼───────┴┐
│ Redis        │      │  PostgreSQL    │
│ - Master     │      │  - Sharded     │
│ - Replicas   │      │  - Partitioned │
│ - Sentinel   │      │  - Multi-AZ    │
└──────────────┘      └────────────────┘

Cost: $20,000+/month
Response time: <20ms
Uptime: 99.99%
```

**Changes:**

- Auto-scaling (10-100 app servers)
- Database sharding (partition data)
- Multi-region deployment
- CDN for static assets
- Kafka for async processing
- Advanced monitoring

---

## Horizontal vs Vertical Scaling

### Vertical Scaling (Scale Up)

**Add more power to existing server:**

```
Before:           After:
2 CPU cores  →    8 CPU cores
4 GB RAM     →    32 GB RAM
100 GB SSD   →    500 GB SSD
```

**Pros:**
✅ Simple (no code changes)  
✅ No distributed system complexity  
✅ Consistent performance

**Cons:**
❌ Limited (hardware has limits)  
❌ Expensive (exponential cost)  
❌ Single point of failure  
❌ Downtime during upgrade

**When to use:**

- Database servers (easier than sharding)
- Cache servers (Redis, Memcached)
- Early stages (simpler to manage)

### Horizontal Scaling (Scale Out)

**Add more servers:**

```
Before:        After:
1 server  →    10 servers (same specs)
```

**Pros:**
✅ Nearly unlimited capacity  
✅ Linear cost scaling  
✅ Better fault tolerance  
✅ No downtime (rolling deployment)

**Cons:**
❌ Code must be stateless  
❌ Distributed system complexity  
❌ Session management needed  
❌ Monitoring more complex

**When to use:**

- Application servers (Spring Boot apps)
- Microservices
- High availability requirements
- Beyond ~10K users

---

## Stateless Service Design

**Critical for horizontal scaling:** Each request can go to ANY server.

### The Problem: Stateful Sessions

```java
// BAD: Storing session in memory
@RestController
public class BadController {
    private Map<String, User> sessions = new HashMap<>(); // ← Stored in THIS server

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        User user = authenticate(request);
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, user); // ← Only on THIS server!
        return sessionId;
    }

    @GetMapping("/profile")
    public User getProfile(@RequestHeader("Session-Id") String sessionId) {
        return sessions.get(sessionId); // ← Only works if routed to SAME server!
    }
}
```

**Problem:**

```
Request 1: Login  → Server A (session stored on A)
Request 2: Profile → Server B (session not found!) ❌
```

### Solution 1: JWT (Stateless Tokens)

```java
// GOOD: No server-side session storage
@RestController
public class AuthController {
    @Autowired
    private JwtTokenProvider jwtProvider;

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        User user = authenticate(request);

        // Create JWT (contains user info, signed by server)
        String token = jwtProvider.createToken(user);
        return token; // Client stores this
    }

    @GetMapping("/profile")
    public User getProfile(@RequestHeader("Authorization") String token) {
        // Verify and extract user from token
        User user = jwtProvider.validateAndGetUser(token);
        return user; // No server-side lookup!
    }
}
```

**How JWT works:**

```
Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEyMywibmFtZSI6IkpvaG4ifQ.signature

Decoded:
{
  "userId": 123,
  "name": "John",
  "exp": 1672531200  ← Expiration
}
+ Signature (prevents tampering)
```

**Benefits:**

- No server-side storage needed
- Works with any server
- Scales infinitely

**JWT vs Session:**

| Feature     | JWT                        | Session                  |
| ----------- | -------------------------- | ------------------------ |
| Storage     | Client                     | Server (Redis/DB)        |
| Scalability | Excellent                  | Good (with shared store) |
| Revocation  | Harder                     | Easy                     |
| Size        | Larger (~500 bytes)        | Small (session ID only)  |
| Use case    | Microservices, mobile apps | Traditional web apps     |

### Solution 2: Shared Session Store

```java
@RestController
public class SessionController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        User user = authenticate(request);
        String sessionId = UUID.randomUUID().toString();

        // Store in Redis (shared across all servers)
        redisTemplate.opsForValue().set(
            "session:" + sessionId,
            objectMapper.writeValueAsString(user),
            30, TimeUnit.MINUTES // TTL
        );

        return sessionId;
    }

    @GetMapping("/profile")
    public User getProfile(@RequestHeader("Session-Id") String sessionId) {
        // Fetch from Redis (works from any server)
        String userData = redisTemplate.opsForValue().get("session:" + sessionId);
        return objectMapper.readValue(userData, User.class);
    }
}
```

**Architecture:**

```
Server A ──┐
Server B ──┼──→ Redis (shared session store)
Server C ──┘
```

---

## Database Scaling

### Read Replicas

**For read-heavy workloads** (90% reads, 10% writes)

```
            ┌──────────────┐
            │  Primary DB  │ ← Writes go here
            │ (Read/Write) │
            └──────┬───────┘
                   │ Replication
          ┌────────┼────────┐
          │        │        │
     ┌────▼───┐ ┌─▼─────┐ ┌▼──────┐
     │Replica1│ │Replica2│ │Replica3│ ← Reads from here
     │(Read)  │ │(Read)  │ │(Read)  │
     └────────┘ └────────┘ └────────┘
```

**Spring Boot configuration:**

```yaml
spring:
  datasource:
    # Primary (writes)
    primary:
      url: jdbc:postgresql://primary-db:5432/payment_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}

    # Read replicas
    replica:
      url: jdbc:postgresql://replica-db:5432/payment_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
```

```java
@Configuration
public class DatabaseConfig {
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // Primary database configuration
    }

    @Bean
    public DataSource replicaDataSource() {
        // Replica database configuration
    }
}

// Usage
@Transactional(readOnly = false)  // Uses primary
public void createPayment(Payment payment) {
    paymentRepo.save(payment);
}

@Transactional(readOnly = true)   // Uses replica
public List<Payment> findAll() {
    return paymentRepo.findAll();
}
```

### Database Sharding

**For write-heavy workloads** or massive data

**Shard by user ID:**

```
Users 1-100K    → Shard 1
Users 100K-200K → Shard 2
Users 200K-300K → Shard 3
```

**Shard by region:**

```
US users   → US Database
EU users   → EU Database
Asia users → Asia Database
```

**Implementation:**

```java
@Service
public class ShardedAccountService {
    private final Map<Integer, DataSource> shards;

    private DataSource getShardForUser(Long userId) {
        int shardId = (int) (userId % shards.size());
        return shards.get(shardId);
    }

    public Account getAccount(Long userId) {
        DataSource shard = getShardForUser(userId);
        // Query from specific shard
    }
}
```

**Challenges:**

- Cross-shard queries are hard
- Rebalancing data is complex
- Need consistent hashing

### Connection Pooling

**Always use connection pools!**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20 # Max connections
      minimum-idle: 5 # Min idle connections
      connection-timeout: 30000 # 30 seconds
      idle-timeout: 600000 # 10 minutes
      max-lifetime: 1800000 # 30 minutes
```

**Sizing:**

```
Pool size = (Core count * 2) + effective_spindle_count
For cloud: ~20-50 connections per app server
```

---

## Caching Strategies

### Cache-Aside Pattern

```java
@Service
public class UserService {
    @Autowired
    private RedisTemplate<String, User> redisTemplate;

    @Autowired
    private UserRepository userRepo;

    public User getUser(Long userId) {
        String cacheKey = "user:" + userId;

        // 1. Try cache first
        User user = redisTemplate.opsForValue().get(cacheKey);
        if (user != null) {
            return user; // Cache hit!
        }

        // 2. Cache miss: fetch from database
        user = userRepo.findById(userId).orElseThrow();

        // 3. Store in cache for next time
        redisTemplate.opsForValue().set(cacheKey, user, 1, TimeUnit.HOURS);

        return user;
    }

    public void updateUser(User user) {
        // Update database
        userRepo.save(user);

        // Invalidate cache
        redisTemplate.delete("user:" + user.getId());
    }
}
```

### Spring Cache Abstraction

```java
@Service
public class ProductService {

    @Cacheable(value = "products", key = "#id")
    public Product getProduct(Long id) {
        // Only called if not in cache
        return productRepo.findById(id).orElseThrow();
    }

    @CacheEvict(value = "products", key = "#product.id")
    public void updateProduct(Product product) {
        productRepo.save(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void clearAllProducts() {
        // Cache cleared
    }
}
```

### What to Cache

**Good candidates:**
✅ User profiles (rarely change)  
✅ Product catalogs  
✅ Exchange rates (change hourly)  
✅ Configuration data  
✅ Expensive query results

**Bad candidates:**
❌ Real-time stock prices  
❌ Bank account balances  
❌ Shopping cart (user-specific)  
❌ Authentication tokens (security risk)

### Cache Invalidation Strategies

**1. TTL (Time To Live):**

```java
redisTemplate.opsForValue().set(key, value, 5, TimeUnit.MINUTES);
```

**2. Write-Through:**

```java
// Update DB and cache together
public void updateUser(User user) {
    userRepo.save(user);
    redisTemplate.opsForValue().set("user:" + user.getId(), user);
}
```

**3. Event-Driven:**

```java
@KafkaListener(topics = "user-updated")
public void onUserUpdated(UserEvent event) {
    redisTemplate.delete("user:" + event.getUserId());
}
```

---

## Load Balancing

### Algorithms

**1. Round Robin (Default):**

```
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A
...
```

**2. Least Connections:**

```
Server A: 10 active connections
Server B: 5 active connections   ← Route here
Server C: 8 active connections
```

**3. IP Hash (Sticky Sessions):**

```
User 192.168.1.1 → Always Server A
User 192.168.1.2 → Always Server B
```

**4. Weighted:**

```
Server A (8 CPU cores): 40% traffic
Server B (4 CPU cores): 20% traffic
Server C (8 CPU cores): 40% traffic
```

### Health Checks

```yaml
# NGINX configuration
upstream backend {
server app1:8080 max_fails=3 fail_timeout=30s;
server app2:8080 max_fails=3 fail_timeout=30s;
server app3:8080 max_fails=3 fail_timeout=30s;

check interval=5000 rise=2 fall=3 timeout=1000;
}
```

**Spring Boot health endpoint:**

```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        // Check database, Redis, etc.
        boolean healthy = checkDependencies();

        if (healthy) {
            return ResponseEntity.ok("OK");
        } else {
            return ResponseEntity.status(503).body("UNHEALTHY");
        }
    }
}
```

---

## Scaling Kafka

### Partitions for Parallelism

```
Topic: payment-events (3 partitions)

Producer                  Kafka                   Consumers
  ↓                                             (Consumer Group)
Payment 1 ─────→ Partition 0 ─────→ Consumer A
Payment 2 ─────→ Partition 1 ─────→ Consumer B
Payment 3 ─────→ Partition 2 ─────→ Consumer C
Payment 4 ─────→ Partition 0 ─────→ Consumer A
```

**Rule:** Max consumers = number of partitions

### Replication

```
Partition 0:
  - Leader: Broker 1
  - Replica: Broker 2
  - Replica: Broker 3

If Broker 1 fails → Broker 2 becomes leader
```

**Configuration:**

```
replication-factor=3  # 3 copies of each partition
min.insync.replicas=2 # At least 2 acknowledged
```

---

## Path to 1M Users

### Month 1: 1-1K Users

- Single server (app + DB)
- Cost: $50/month
- **Action**: Build features, monitor metrics

### Month 3: 1K-10K Users

- Add load balancer
- 2-3 app servers
- Single database (still OK)
- Cost: $300/month
- **Action**: Optimize queries, add indexes

### Month 6: 10K-50K Users

- 5-10 app servers (auto-scaling)
- Redis cache
- Database read replicas (3)
- Cost: $2,000/month
- **Action**: Cache aggressively, optimize hot paths

### Month 12: 50K-100K Users

- 10-20 app servers
- Database sharding consideration
- Kafka for async processing
- CDN for static assets
- Cost: $10,000/month
- **Action**: Shard database, add monitoring

### Month 24: 100K-1M Users

- Auto-scaling (20-100 app servers)
- Sharded databases (10 shards)
- Multi-region deployment
- Advanced caching (L1 + L2)
- Cost: $50,000+/month
- **Action**: Multi-region, chaos engineering

---

## Summary

**Key Takeaways:**

✅ **Horizontal Scaling**: Add more servers, not bigger ones  
✅ **Stateless Design**: JWT or shared sessions (Redis)  
✅ **Database**: Read replicas, then sharding  
✅ **Caching**: Redis for read-heavy data  
✅ **Load Balancing**: Distribute traffic evenly  
✅ **Kafka**: More partitions = more parallelism

**Scaling Checklist:**

- [ ] Stateless application design
- [ ] Database connection pooling
- [ ] Redis caching for hot data
- [ ] Read replicas for queries
- [ ] Horizontal pod autoscaling
- [ ] Load balancer with health checks
- [ ] CDN for static assets
- [ ] Monitoring and alerting

**Next Steps:**

- Read [load-test-guide.md](load-test-guide.md) to validate your scaling strategy
- Read [java-concurrency.md](java-concurrency.md) for thread safety
- Study our microservices for stateless design examples

From MVP to 1M users - you've got this! 🚀
