# DynamoDB Introduction & Best Practices

A comprehensive guide to Amazon DynamoDB for developers coming from a relational background (PostgreSQL/MySQL).

## Table of Contents

- [DynamoDB Introduction \& Best Practices](#dynamodb-introduction--best-practices)
  - [Table of Contents](#table-of-contents)
  - [What is DynamoDB?](#what-is-dynamodb)
  - [Key Concepts](#key-concepts)
    - [1. Tables](#1-tables)
    - [2. Items](#2-items)
    - [3. Primary Key (PK)](#3-primary-key-pk)
    - [4. Attributes](#4-attributes)
    - [5. Indexes](#5-indexes)
  - [DynamoDB vs PostgreSQL](#dynamodb-vs-postgresql)
  - [Data Modeling](#data-modeling)
    - [Single Table Design](#single-table-design)
  - [Access Patterns](#access-patterns)
  - [Local Development](#local-development)
  - [Spring Boot Integration](#spring-boot-integration)
    - [Dependencies](#dependencies)
    - [Configuration](#configuration)
    - [Entity](#entity)
    - [Repository](#repository)
  - [Summary](#summary)

---

## What is DynamoDB?

Amazon DynamoDB is a **fully managed, serverless, key-value NoSQL database** designed to run high-performance applications at any scale.

**Key Characteristics:**

- **Serverless**: No servers to provision or manage.
- **Performance**: Single-digit millisecond latency at any scale.
- **Scalability**: Automatically scales up/down (from 5 to 5M requests/sec).
- **Durability**: Data replicated across 3 Availability Zones (AZs).

---

## Key Concepts

### 1. Tables

A collection of items. Unlike SQL, there are no fixed schemas (except for the primary key).

### 2. Items

A single data record (like a row in SQL). Can have any number of attributes. Max size: 400KB.

```json
{
  "PK": "USER#123",
  "SK": "PROFILE",
  "name": "John Doe",
  "email": "john@example.com",
  "age": 30,
  "tags": ["premium", "verified"]
}
```

### 3. Primary Key (PK)

Uniquely identifies an item. Two types:

- **Simple Primary Key**: Partition Key (PK) only.
- **Composite Primary Key**: Partition Key (PK) + Sort Key (SK).

### 4. Attributes

Data fields (like columns). Can be scalar (String, Number, Boolean) or complex (List, Map, Set).

### 5. Indexes

- **LSI (Local Secondary Index)**: Same PK, different SK. Must be created at table creation.
- **GSI (Global Secondary Index)**: Different PK and SK. Can be created anytime.

---

## DynamoDB vs PostgreSQL

| Feature          | PostgreSQL (Relational)          | DynamoDB (NoSQL)                 |
| ---------------- | -------------------------------- | -------------------------------- |
| **Schema**       | Rigid, defined upfront           | Flexible (schema-on-read)        |
| **Scaling**      | Vertical (bigger server)         | Horizontal (more partitions)     |
| **Joins**        | Supported (complex queries)      | Not supported (denormalize data) |
| **Transactions** | ACID (strong consistency)        | ACID (supported but costs more)  |
| **Querying**     | Flexible SQL                     | Access patterns only (PK/SK)     |
| **Best for**     | Complex relationships, analytics | High throughput, simple lookups  |

---

## Data Modeling

**The Golden Rule**: Design your schema based on your **Access Patterns**, not your data structure.

### Single Table Design

In DynamoDB, you often put multiple entity types (Users, Orders, Products) in ONE table to enable pre-joined data retrieval.

**Example Schema:**

| PK       | SK        | Data                                |
| -------- | --------- | ----------------------------------- |
| USER#123 | PROFILE   | { name: "John", email: "..." }      |
| USER#123 | ORDER#999 | { total: 50.00, status: "PAID" }    |
| USER#123 | ORDER#998 | { total: 25.00, status: "SHIPPED" } |

**Query:** `Get all data for User 123`

- `SELECT * FROM Table WHERE PK = "USER#123"`
- Returns Profile AND Orders in one request! (Efficient)

---

## Access Patterns

Define these **before** writing code.

**Scenario: Session Management**

1. **Create Session**: Write new item with TTL.
2. **Get Session**: Read by SessionID.
3. **Update Session**: Update last accessed time.
4. **Delete Session**: Remove item (or let TTL expire).

**Table Design:**

- **PK**: `SESSION#{sessionId}`
- **SK**: `METADATA` (not strictly needed for simple KV, but good practice)
- **Attributes**: `userId`, `createdAt`, `expiresAt`, `data` (JSON)

---

## Local Development

Use **DynamoDB Local** (Docker) to develop without an AWS account.

```yaml
# docker-compose.yml
services:
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    ports:
      - "8000:8000"
    command: "-jar DynamoDBLocal.jar -sharedDb"
```

**GUI Tool**: `NoSQL Workbench for DynamoDB` or `dynamodb-admin`.

---

## Spring Boot Integration

### Dependencies

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb-enhanced</artifactId>
    <version>2.20.0</version>
</dependency>
```

### Configuration

```java
@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .endpointOverride(URI.create("http://localhost:8000"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("fake", "fake")))
            .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(client)
            .build();
    }
}
```

### Entity

```java
@DynamoDbBean
public class Session {
    private String sessionId;
    private String userId;
    private Long ttl;

    @DynamoDbPartitionKey
    public String getSessionId() { return sessionId; }
    public void setSessionId(String id) { this.sessionId = id; }

    // Standard getters/setters
}
```

### Repository

```java
@Repository
public class SessionRepository {
    private final DynamoDbTable<Session> sessionTable;

    public SessionRepository(DynamoDbEnhancedClient client) {
        this.sessionTable = client.table("Sessions", TableSchema.fromBean(Session.class));
    }

    public void save(Session session) {
        sessionTable.putItem(session);
    }

    public Session findById(String id) {
        return sessionTable.getItem(Key.builder().partitionValue(id).build());
    }
}
```

---

## Summary

**When to use DynamoDB:**

- High-scale session storage (millions of active users).
- Shopping carts.
- Real-time voting/leaderboards.
- Metadata stores.

**When NOT to use DynamoDB:**

- Complex analytical queries (use SQL or Elasticsearch).
- Ad-hoc reporting.
- Relationships are constantly changing.

DynamoDB is a power tool. Used correctly, it scales infinitely. Used like SQL, it's painful and expensive.
