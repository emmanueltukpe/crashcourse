# Elasticsearch Introduction & Integration

A guide to adding full-text search and analytics to our payment system using Elasticsearch.

## Table of Contents

1. [What is Elasticsearch?](#what-is-elasticsearch)
2. [Key Concepts](#key-concepts)
3. [Elasticsearch vs Relational DB](#elasticsearch-vs-relational-db)
4. [Architecture Pattern (CQRS)](#architecture-pattern-cqrs)
5. [Integration Steps](#integration-steps)
6. [Querying](#querying)

---

## What is Elasticsearch?

Elasticsearch is a **distributed, RESTful search and analytics engine** capable of addressing a growing number of use cases. It is the heart of the Elastic Stack (ELK).

**Key Features:**
- **Full-Text Search**: Powerful search capabilities (fuzzy matching, stemming, relevance scoring).
- **Near Real-Time**: Documents are indexed and searchable within 1 second.
- **Distributed**: Scales horizontally to petabytes of data.
- **REST API**: JSON over HTTP.

---

## Key Concepts

### 1. Index
Equivalent to a **Database** in SQL. A collection of documents.
Example: `payments-index`

### 2. Document
Equivalent to a **Row** in SQL. A JSON object stored in an index.
```json
{
  "id": "1",
  "userId": "100",
  "amount": 50.00,
  "description": "Payment for coffee",
  "timestamp": "2023-10-27T10:00:00Z"
}
```

### 3. Field
Equivalent to a **Column** in SQL. Key-value pair in a document.

### 4. Mapping
Equivalent to a **Schema** in SQL. Defines how fields are indexed (text, keyword, date, etc.).

### 5. Shard
An index can be split into multiple shards (distributed across nodes).

---

## Elasticsearch vs Relational DB

| Feature | Relational DB (PostgreSQL) | Elasticsearch |
|---------|----------------------------|---------------|
| **Primary Use** | Transactional (OLTP), Consistency | Search, Analytics, Logging |
| **Data Structure** | Normalized, Tables, Rows | Denormalized, JSON Documents |
| **Transactions** | ACID (Strong) | Eventual Consistency |
| **Search** | Exact match, simple LIKE | Relevance scoring, fuzzy, aggregation |
| **Speed** | Fast for ID lookups | Fast for text search & aggregation |

---

## Architecture Pattern (CQRS)

We use **CQRS (Command Query Responsibility Segregation)** with **Event Sourcing** to integrate Elasticsearch.

**Flow:**
1. **Write**: User creates payment → `Payment Service` → PostgreSQL (Primary Source of Truth).
2. **Publish**: `Payment Service` publishes `PaymentCreatedEvent` to Kafka.
3. **Consume**: `Search Service` listens to Kafka topic.
4. **Index**: `Search Service` saves document to Elasticsearch.
5. **Read**: User searches payments → `Search Service` → Elasticsearch.

**Benefits:**
- **Decoupling**: Search logic is separate from core payment logic.
- **Performance**: Complex searches don't load the primary database.
- **Scalability**: Scale search independently of transactions.

---

## Integration Steps

### 1. Docker Compose
Add Elasticsearch and Kibana (visualization tool).

```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:7.17.10
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"

  kibana:
    image: docker.elastic.co/kibana/kibana:7.17.10
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
```

### 2. Spring Boot Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

### 3. Document Entity

```java
@Document(indexName = "payments")
public class PaymentDocument {
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String userId;
    
    @Field(type = FieldType.Double)
    private BigDecimal amount;
    
    @Field(type = FieldType.Text) // Full-text search
    private String description;
    
    @Field(type = FieldType.Date)
    private Instant createdAt;
}
```

### 4. Repository

```java
public interface PaymentSearchRepository extends ElasticsearchRepository<PaymentDocument, String> {
    List<PaymentDocument> findByDescriptionContaining(String description);
    List<PaymentDocument> findByUserId(String userId);
}
```

---

## Querying

**REST API (via Kibana Dev Tools or curl):**

```json
GET /payments/_search
{
  "query": {
    "multi_match": {
      "query": "coffee",
      "fields": ["description"]
    }
  }
}
```

**Spring Data:**

```java
// Fuzzy search
NativeSearchQuery query = new NativeSearchQueryBuilder()
    .withQuery(QueryBuilders.fuzzyQuery("description", "cofee"))
    .build();

SearchHits<PaymentDocument> hits = elasticsearchTemplate.search(query, PaymentDocument.class);
```

---

## Summary

Elasticsearch adds powerful search capabilities to our payment system without compromising the transactional integrity of PostgreSQL. By using Kafka to sync data, we achieve a robust, decoupled architecture.
