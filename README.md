# Cross-Border Payment Simulator

> **A hands-on learning project for mastering Spring Boot, Java, and microservices architecture**

A production-grade microservices system that simulates cross-border payments with multi-currency conversion (USD ⇄ Stablecoin ⇄ NGN). Built incrementally across 5 stages, from basic REST APIs to advanced distributed systems patterns.

## 📚 What You'll Learn

- Java fundamentals and object-oriented programming
- Spring Boot framework (DI, annotations, auto-configuration)
- RESTful API design and implementation
- Database transactions and ACID properties
- JPA/Hibernate for database access
- Microservices architecture patterns
- Docker and containerization
- PostgreSQL and relational databases

**Stage 1 (Current)** covers core concepts. Stages 2-5 will add Kafka messaging, concurrency patterns, Kubernetes deployment, and system design principles.

---

## 🏗️ Architecture (Stage 1)

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP requests
       ↓
┌─────────────────────────────────────────┐
│          API Gateway (8080)             │
│  Routes requests to microservices       │
└─────────────┬──────────────┬────────────┘
              │              │
       ┌──────┴──────┐  ┌────┴──────────┐
       ↓             ↓  ↓               ↓
┌─────────────┐  ┌──────────────┐   ┌────────────┐
│Auth Service │  │Account Service│   │Mock Exchange│
│   (8081)    │  │    (8082)     │   │   (8084)   │
│             │  │               │   │            │
│• Register   │  │•Balances      │←──│•Get Quote  │
│• Login      │  │•Convert $     │   │•Execute    │
│• JWT tokens │  │•@Transactional│   │            │
└─────┬───────┘  └───────┬───────┘   └────────────┘
      │                  │
      ↓                  ↓
 ┌─────────────────────────┐
 │   PostgreSQL (5432)     │
 │  ┌──────────┬──────────┐│
 │  │ auth_db  │account_db││
 │  └──────────┴──────────┘│
 └─────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Docker Desktop
- Maven (included via wrapper)

### Running with Docker (Recommended)

```bash
# 1. Clone/navigate to project
cd /Users/emmanuel/Downloads/crashcourse

# 2. Make init script executable
chmod +x infra/init-databases.sh

# 3. Start all services
docker-compose up --build

# 4. Test the system
curl http://localhost:8080/api/v1/auth/health
```

**Ports:**
- 8080 - API Gateway (use this for all requests)
- 8081 - Auth Service
- 8082 - Account Service  
- 8084 - Mock Exchange
- 5432 - PostgreSQL

### Testing the Flow

```bash
# 1. Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# 2. Create account with $1000 USD
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"initialUsdBalance":1000}'

# 3. Convert $100 USD to NGN
curl -X POST http://localhost:8080/api/v1/accounts/convert \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"fromCurrency":"USD","toCurrency":"NGN","amount":100}'

# 4. Check updated balance
curl http://localhost:8080/api/v1/accounts/1
```

See [How to Run Stage 1](./docs/how-to-run-stage-1.md) for detailed instructions.

---

## 📖 Documentation

Educational guides in `docs/`:

- **[Java Basics](./docs/java-basics.md)** - Variables, classes, objects, collections, exceptions
- **[Spring Boot Basics](./docs/spring-boot-basics.md)** - DI/IoC, annotations, layers, configuration
- **[Transactional Basics](./docs/transactional-basics.md)** - ACID properties, @Transactional, rollback
- **[How to Run Stage 1](./docs/how-to-run-stage-1.md)** - Setup and testing guide

Each microservice also contains extensive inline code comments explaining concepts as you read the code.

---

## 🗂️ Project Structure

```
crossborder-sim/
├── common/                    # Shared DTOs and enums
│   └── src/main/java/
│       └── com/learn/common/
│           ├── dto/          # Request/Response objects
│           ├── enums/        # Currency enum
│           └── exception/    # Custom exceptions
│
├── auth-service/             # User authentication
│   └── src/main/java/
│       └── com/learn/authservice/
│           ├── controller/   # REST endpoints
│           ├── entity/       # User JPA entity
│           ├── repository/   # Spring Data JPA
│           ├── service/      # Business logic + BCrypt
│           └── util/         # JWT generation
│
├── account-service/          # Balance management
│   └── src/main/java/
│       └── com/learn/accountservice/
│           ├── controller/   # Account endpoints
│           ├── entity/       # Account entity
│           ├── repository/   # With pessimistic locking
│           ├── service/      # @Transactional conversion
│           └── client/       # ExchangeClient (WebClient)
│
├── mock-exchange/            # Simulated exchange API
│   └── src/main/java/
│       └── com/learn/mockexchange/
│           ├── controller/   # Quote & Execute endpoints
│           └── service/      # Rate calculation + fees
│
├── gateway/                  # API Gateway
│   └── src/main/resources/
│       └── application.yml   # Route configuration
│
├── docs/                     # Educational documentation
├── infra/                    # Infrastructure scripts
├── docker-compose.yml        # Multi-container orchestration
└── Dockerfile                # Multi-stage build
```

---

## 🎯 Key Features (Stage 1)

### Authentication (auth-service)
- ✅ Email/password registration
- ✅ BCrypt password hashing
- ✅ JWT token generation
- ✅ PostgreSQL persistence

### Account Management (account-service)
- ✅ Multi-currency balances (USD, NGN, USDC)
- ✅ Currency conversion with external exchange
- ✅ **@Transactional** for ACID compliance
- ✅ Pessimistic locking for concurrency
- ✅ BigDecimal for precise financial calculations

### Exchange Integration (mock-exchange)
- ✅ Quote generation with rates & fees
- ✅ Trade execution with quote IDs
- ✅ Simulated network latency
- ✅ Random failures for testing resilience

### Infrastructure
- ✅ Docker Compose orchestration
- ✅ Multi-stage Docker builds
- ✅ Health checks & dependency ordering
- ✅ PostgreSQL with multiple databases

---

## 💡 Technical Highlights

### ACID Transactions

The conversion flow demonstrates database transactions:

```java
@Transactional
public ConvertResponse convert(Long userId, Currency from, Currency to, BigDecimal amount) {
    // 1. Lock account (prevents concurrent modifications)
    Account account = accountRepo.findByUserIdForUpdate(userId).orElseThrow();
    
    // 2. Validate balance
    if (account.getBalance(from).compareTo(amount) < 0) {
        throw new InsufficientFundsException();  // ROLLBACK!
    }
    
    // 3. Get quote from exchange
    QuoteResponse quote = exchangeClient.getQuote(...);
    
    // 4. Execute trade
    ExecuteTradeResponse result = exchangeClient.executeTrade(...);
    
    if (!result.isSuccess()) {
        throw new ExchangeUnavailableException();  // ROLLBACK!
    }
    
    // 5. Update balances atomically
    account.debit(from, amount);
    account.credit(to, convertedAmount);
    accountRepo.save(account);
    
    // Success → COMMIT (database changes are permanent)
    return new ConvertResponse(...);
}
```

**If ANY exception occurs**, all database changes are rolled back automatically!

### Dependency Injection

Spring Boot manages all objects:

```java
@Service
public class AccountService {
    private final AccountRepository accountRepo;
    private final ExchangeClient exchangeClient;
    
    // Spring automatically provides dependencies via constructor
    public AccountService(AccountRepository accountRepo, ExchangeClient exchangeClient) {
        this.accountRepo = accountRepo;
        this.exchangeClient = exchangeClient;
    }
}
```

### API Gateway Routing

Single entry point routes to appropriate services:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/auth/**
        
        - id: account-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/v1/accounts/**
```

---

## 🧪 Testing

### Manual Testing

See test scripts in `docs/how-to-run-stage-1.md`

### Database Verification

```bash
# Connect to PostgreSQL
docker exec -it crossborder-postgres psql -U postgres

# Check users
\c auth_db
SELECT * FROM  users;

# Check accounts
\c account_db
SELECT user_id, usd_balance, ngn_balance, stablecoin_balance FROM accounts;
```

### Automated Tests

```bash
# Run all tests
./mvnw test

# Run specific service tests
cd auth-service
../mvnw test
```

---

## 🛠️ Development

### Building

```bash
# Build all modules
./mvnw clean package

# Build specific module
cd auth-service
../mvnw package
```

### Running Locally (without Docker)

```bash
# Start PostgreSQL (Docker)
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15

# Run each service in separate terminals
cd mock-exchange && java -jar target/*.jar
cd auth-service && java -jar target/*.jar
cd account-service && java -jar target/*.jar
cd gateway && java -jar target/*.jar
```

---

## 🗺️ Roadmap

### ✅ Stage 1 - Foundation (CURRENT)
- Microservices architecture
- ACID transactions
- REST APIs
- Docker infrastructure

### 🔜 Stage 2 - Messaging & Async
- Apache Kafka integration
- Transactional Outbox pattern
- Saga pattern for distributed transactions
- Payment & Ledger services

### 🔜 Stage 3 - Scalability
- Optimistic locking
- Concurrency handling
- Load testing (k6)
- Scaling strategies

### 🔜 Stage 4 - Production Infra
- Kubernetes deployment
- Helm charts
- AWS integration (EKS, RDS)
- CI/CD pipelines
- Observability (Prometheus, Grafana)

### 🔜 Stage 5 - System Design
- Event Sourcing & CQRS
- Disaster Recovery
- Security & Compliance
- Interview preparation

---

## 📝 License

Educational project - feel free to use for learning!

---

## 🙏 Acknowledgments

Built as a comprehensive learning resource for:
- Spring Boot and microservices
- Financial system design
- Production-ready architecture patterns

Happy coding! 🚀
