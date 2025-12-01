# How to Run Stage 1 - Step-by-Step Guide

This guide walks you through running the cross-border payment simulator locally using Docker Compose.

## Prerequisites

Before starting, ensure you have the following installed:

### Required Software

1. **Java 17 or later**
   ```bash
   java -version
   # Should show: java version "17" or higher
   ```
   Download from: https://adoptium.net/

2. **Docker Desktop**
   ```bash
   docker --version
   docker-compose --version
   ```
   Download from: https://www.docker.com/products/docker-desktop/

3. **Maven** (included via Maven Wrapper - `./mvnw`)
   ```bash
   ./mvnw --version
   ```

4. **Git** (optional, for version control)
   ```bash
   git --version
   ```

### Optional but Recommended

- **curl** or **Postman** - for testing API endpoints
- **pgAdmin** or **DBeaver** - for viewing database contents

---

## Quick Start (Docker)

The fastest way to run everything:

```bash
# 1. Navigate to project directory
cd /Users/emmanuel/Downloads/crashcourse

# 2. Make init script executable
chmod +x infra/init-databases.sh

# 3. Build and start all services
docker-compose up --build

# 4. Wait for all services to start (about 2-3 minutes)
# You should see logs from all services

# 5. Test that everything is running
curl http://localhost:8080/api/v1/auth/health
```

### Stopping Services

```bash
# Stop all services (keep data)
docker-compose down

# Stop and remove all data
docker-compose down -v
```

---

## Step-by-Step Guide (Without Docker)

If you prefer to run services individually:

### 1. Start PostgreSQL

Option A: Using Docker
```bash
docker run --name crossborder-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_USER=postgres \
  -p 5432:5432 \
  -d postgres:15-alpine

# Create databases
docker exec -it crossborder-postgres psql -U postgres -c "CREATE DATABASE auth_db;"
docker exec -it crossborder-postgres psql -U postgres -c "CREATE DATABASE account_db;"
```

Option B: Local PostgreSQL install
- Install PostgreSQL 15
- Create databases:
  ```sql
  CREATE DATABASE auth_db;
  CREATE DATABASE account_db;
  ```

### 2. Build All Services

```bash
cd /Users/emmanuel/Downloads/crashcourse
./mvnw clean package -DskipTests
```

This creates JAR files in `target/` directories.

### 3. Start Services

Open **4 terminal windows** and run each service:

**Terminal 1 - Mock Exchange:**
```bash
cd mock-exchange
java -jar target/mock-exchange-0.0.1-SNAPSHOT.jar
# Runs on port 8084
```

**Terminal 2 - Auth Service:**
```bash
cd auth-service
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
# Runs on port 8081
```

**Terminal 3 - Account Service:**
```bash
cd account-service
java -jar target/account-service-0.0.1-SNAPSHOT.jar
# Runs on port 8082
```

**Terminal 4 - Gateway:**
```bash
cd gateway
java -jar target/gateway-0.0.1-SNAPSHOT.jar
# Runs on port 8080
```

---

## Testing the System

### 1. Health Checks

Verify all services are running:

```bash
# Gateway
curl http://localhost:8080/api/v1/auth/health

# Auth Service (direct)
curl http://localhost:8081/api/v1/auth/health

# Account Service (direct)
curl http://localhost:8082/api/v1/accounts/health

# Mock Exchange
curl http://localhost:8084/api/health
```

All should return: `"[Service name] is running"`

### 2. Register a User

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "securePassword123"
  }'
```

Expected response:
```json
{
  "userId": 1,
  "email": "alice@example.com",
  "message": "User registered successfully"
}
```

### 3. Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "securePassword123"
  }'
```

Expected response:
```json
{
  "token": "eyJhbGci...",
  "email": "alice@example.com",
  "userId": 1,
  "expiresIn": 86400,
  "message": "Login successful"
}
```

Save the `token` for future requests (not needed for Stage 1, but will be in later stages).

### 4. Create Account with Initial Balance

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "initialUsdBalance": 1000.00
  }'
```

Expected response:
```json
{
  "id": 1,
  "userId": 1,
  "usdBalance": 1000.00,
  "ngnBalance": 0.00,
  "stablecoinBalance": 0.00,
  ...
}
```

### 5. Get Account Details

```bash
curl http://localhost:8080/api/v1/accounts/1
```

### 6. Convert Currency (USD → NGN)

```bash
curl -X POST http://localhost:8080/api/v1/accounts/convert \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "fromCurrency": "USD",
    "toCurrency": "NGN",
    "amount": 100.00
  }'
```

Expected response:
```json
{
  "convertedAmount": 149000.00,
  "exchangeRate": 1500.00,
  "fees": 1.00,
  "fromCurrency": "USD",
  "toCurrency": "NGN",
  "originalAmount": 100.00,
  "message": "Conversion successful"
}
```

### 7. Verify Balance Updated

```bash
curl http://localhost:8080/api/v1/accounts/1
```

You should see:
- `usdBalance`: 900.00 (1000 - 100)
- `ngnBalance`: 149000.00 (new)

---

## Viewing Database Data

Connect to PostgreSQL:

```bash
# Using Docker
docker exec -it crossborder-postgres psql -U postgres

# Switch to auth database
\c auth_db

# View users
SELECT id, email, password_hash, created_at FROM users;

# Switch to account database
\c account_db

# View accounts
SELECT user_id, usd_balance, ngn_balance, stablecoin_balance FROM accounts;
```

---

## Viewing Logs

### Docker Compose

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f auth-service
docker-compose logs -f account-service
```

### Individual JARs

Logs appear in the terminal where you ran the service.

---

## Troubleshooting

### Port Already in Use

```
Error: Port 8080 is already allocated
```

**Solution**: Stop the conflicting service or change the port in `application.yml`

```bash
# Find what's using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>
```

### Database Connection Failed

```
Error: Connection refused to localhost:5432
```

**Solution**: Ensure PostgreSQL is running:
```bash
docker ps  # Check if postgres container is running
```

### Service Won't Start

**Solution**: Check logs for specific error:
```bash
docker-compose logs auth-service
```

Common issues:
- Database not ready → Wait and retry
- Port in use → Change port
- Build failed → Run `./mvnw clean package` again

### Mock Exchange Returns "Unavailable"

This is **intentional**! The mock-exchange randomly fails 5% of requests to simulate real API behavior. Just retry the conversion.

---

## Complete End-to-End Test Script

Save this as `test-stage-1.sh`:

```bash
#!/bin/bash

API="http://localhost:8080"

echo "1. Registering user..."
curl -X POST $API/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

echo -e "\n\n2. Logging in..."
curl -X POST $API/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

echo -e "\n\n3. Creating account..."
curl -X POST $API/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"initialUsdBalance":1000}'

echo -e "\n\n4. Getting account..."
curl $API/api/v1/accounts/1

echo -e "\n\n5. Converting USD to NGN..."
curl -X POST $API/api/v1/accounts/convert \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"fromCurrency":"USD","toCurrency":"NGN","amount":100}'

echo -e "\n\n6. Verifying balance..."
curl $API/api/v1/accounts/1

echo -e "\n\nDone!"
```

Run it:
```bash
chmod +x test-stage-1.sh
./test-stage-1.sh
```

---

## Next Steps

- Explore the code in each service
- Read the documentation in `docs/`
- Try converting to different currencies (USD ↔ USDC ↔ NGN)
- Test error scenarios (insufficient funds, invalid user ID)
- Check the logs to see transaction behavior

When ready, proceed to **Stage 2** to add Kafka messaging and distributed transactions!
