# Cross-Border Payment Simulator - Setup Guide

Welcome! This guide will help you set up and run the Cross-Border Payment Simulator project from scratch. We've written this for complete beginners, so we'll explain every step and technical term along the way.

---

## Table of Contents

1. [What is This Project?](#what-is-this-project)
2. [Prerequisites](#prerequisites)
3. [Installing Dependencies](#installing-dependencies)
4. [Building the Project](#building-the-project)
5. [Running the Application](#running-the-application)
6. [Understanding Environment Variables](#understanding-environment-variables)
7. [Spring Boot Configuration Deep Dive](#spring-boot-configuration-deep-dive)
8. [Project Environment Variables Reference](#project-environment-variables-reference)
9. [Spring Profiles Explained](#spring-profiles-explained)
10. [Port Configuration Reference](#port-configuration-reference)
11. [Troubleshooting](#troubleshooting)

---

## What is This Project?

This is a **microservices** project that simulates a cross-border payment system (like sending money between countries or converting currencies).

### What are Microservices?

Imagine a restaurant. Instead of one person doing everything (taking orders, cooking, serving, cleaning), you have specialized workers:

- **Host** - Greets customers and directs them
- **Chef** - Cooks the food
- **Waiter** - Takes orders and serves food
- **Cashier** - Handles payments

**Microservices** work the same way. Instead of one big program doing everything, we have several small, specialized programs that work together:

| Service             | What It Does                                       | Restaurant Analogy                 |
| ------------------- | -------------------------------------------------- | ---------------------------------- |
| **gateway**         | Entry point - routes requests to the right service | Host                               |
| **auth-service**    | Handles user registration and login                | Security guard checking IDs        |
| **account-service** | Manages user accounts and currency conversions     | Cashier                            |
| **mock-exchange**   | Simulates currency exchange rates                  | Currency exchange booth            |
| **common**          | Shared code used by other services                 | Employee handbook everyone follows |

### What Technologies Do We Use?

- **Java 21** - The programming language
- **Spring Boot** - A framework that makes it easier to build Java web applications
- **Maven** - A tool that downloads libraries and builds our code
- **Docker** - A tool that packages our apps into containers (like shipping containers for software)
- **PostgreSQL** - A database to store user and account information

---

## Prerequisites

Before you can run this project, you need to install some software on your computer.

### Required Software

| Software                   | Minimum Version | What It Does                                 |
| -------------------------- | --------------- | -------------------------------------------- |
| Java Development Kit (JDK) | 21              | Runs Java programs                           |
| Maven                      | 3.6+            | Builds Java projects (included with project) |
| Docker                     | 20.10+          | Runs containerized applications              |
| Docker Compose             | 2.0+            | Manages multiple Docker containers           |
| Git                        | 2.0+            | Downloads and manages code                   |

### How to Check If You Already Have Them

Open your **Terminal** (Mac/Linux) or **Command Prompt** (Windows) and run these commands:

```bash
# Check Java version
java -version
# Should show: openjdk version "21.x.x" or similar

# Check Docker version
docker --version
# Should show: Docker version 20.x.x or higher

# Check Docker Compose version
docker-compose --version
# Should show: Docker Compose version v2.x.x or higher

# Check Git version
git --version
# Should show: git version 2.x.x
```

---

## Installing Dependencies

### Installing Java 21

Java is the programming language this project is written in. We need JDK (Java Development Kit) version 21.

#### On macOS (using Homebrew)

```bash
# Install Homebrew if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 21
brew install openjdk@21

# Add Java to your PATH (add this to your ~/.zshrc or ~/.bash_profile)
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### On Windows

1. Download the installer from [Adoptium](https://adoptium.net/temurin/releases/?version=21)
2. Run the installer and follow the prompts
3. Make sure to check "Set JAVA_HOME variable" during installation

#### On Ubuntu/Linux

```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

### Installing Docker

Docker allows us to run all our services in isolated containers.

#### On macOS

1. Download [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)
2. Open the downloaded `.dmg` file
3. Drag Docker to Applications
4. Open Docker from Applications - it will run in the background

#### On Windows

1. Download [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
2. Run the installer
3. Restart your computer when prompted
4. Open Docker Desktop

#### On Ubuntu/Linux

```bash
# Update packages
sudo apt update

# Install Docker
sudo apt install docker.io docker-compose-v2

# Add your user to the docker group (so you don't need sudo)
sudo usermod -aG docker $USER

# Log out and log back in for the group change to take effect
```

### Installing Git

Git is used to download and manage code.

#### On macOS

```bash
brew install git
```

#### On Windows

Download and install from [git-scm.com](https://git-scm.com/download/win)

#### On Ubuntu/Linux

```bash
sudo apt install git
```

---

## Building the Project

**Building** means converting our human-readable code into a format the computer can run. We use Maven for this.

### Step 1: Clone the Repository

First, download the project code:

```bash
# Download the project
git clone <your-repository-url>

# Go into the project folder
cd crashcourse
```

### Step 2: Build with Maven

This project includes a **Maven Wrapper** (`mvnw`), which is a script that downloads and runs the correct version of Maven automatically. You don't need to install Maven separately!

```bash
# On Mac/Linux: Build all services
./mvnw clean install -DskipTests

# On Windows: Build all services
mvnw.cmd clean install -DskipTests
```

**What does this command do?**

- `./mvnw` - Run the Maven Wrapper script
- `clean` - Delete any previous build files (start fresh)
- `install` - Compile the code and create JAR files
- `-DskipTests` - Skip running tests (faster for first build)

**Expected output:**
You should see output ending with:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Building Individual Services

If you only want to build one service:

```bash
# Build only auth-service (and its dependencies)
./mvnw clean package -DskipTests -pl auth-service -am

# Build only account-service
./mvnw clean package -DskipTests -pl account-service -am

# Build only mock-exchange
./mvnw clean package -DskipTests -pl mock-exchange -am

# Build only gateway
./mvnw clean package -DskipTests -pl gateway -am
```

**What do these flags mean?**

- `-pl auth-service` - Build only the specified module (project list)
- `-am` - Also build modules that this module depends on (also make)

---

## Running the Application

There are two ways to run the application:

1. **Using Docker Compose** (Recommended) - Runs everything in containers
2. **Running locally** - For development/debugging

### Option 1: Using Docker Compose (Recommended)

Docker Compose starts all services at once with a single command.

#### Starting All Services

```bash
# Start all services in the background
docker-compose up -d --build

# The --build flag rebuilds images if code changed
# The -d flag runs in "detached" mode (background)
```

**What happens when you run this?**

1. Docker builds images for each service (using the Dockerfile)
2. Docker creates a network so services can talk to each other
3. Docker starts PostgreSQL database first
4. Docker waits for database to be healthy
5. Docker starts the other services in dependency order

#### Checking Service Status

```bash
# See which containers are running
docker-compose ps

# Expected output:
# NAME                          STATUS
# crossborder-postgres          Up (healthy)
# crossborder-mock-exchange     Up (healthy)
# crossborder-auth-service      Up (healthy)
# crossborder-account-service   Up (healthy)
# crossborder-gateway           Up
```

#### Viewing Logs

```bash
# View logs from all services
docker-compose logs -f

# View logs from a specific service
docker-compose logs -f auth-service
docker-compose logs -f gateway

# Press Ctrl+C to stop viewing logs
```

#### Stopping All Services

```bash
# Stop and remove containers
docker-compose down

# Stop and remove containers AND delete database data
docker-compose down -v
```

### Option 2: Running Locally (Development Mode)

For local development, you need to:

1. Start PostgreSQL manually
2. Start each service individually

#### Step 1: Start PostgreSQL

```bash
# Start only PostgreSQL from docker-compose
docker-compose up -d postgres

# Wait for it to be healthy
docker-compose ps
```

#### Step 2: Start Services Individually

Open separate terminal windows for each service:

```bash
# Terminal 1: Start mock-exchange
./mvnw spring-boot:run -pl mock-exchange

# Terminal 2: Start auth-service
./mvnw spring-boot:run -pl auth-service

# Terminal 3: Start account-service
./mvnw spring-boot:run -pl account-service

# Terminal 4: Start gateway
./mvnw spring-boot:run -pl gateway
```

### Testing the Application

Once everything is running, test it with these commands:

```bash
# Test health endpoints
curl http://localhost:8081/api/v1/auth/health    # Auth service
curl http://localhost:8082/api/v1/accounts/health # Account service
curl http://localhost:8084/api/health            # Mock exchange
curl http://localhost:8080/api/v1/auth/health    # Through gateway

# Register a new user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "securePassword123"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "securePassword123"}'
```

---

## Understanding Environment Variables

This section explains what environment variables are and how they're used in this project.

### What Are Environment Variables?

**Environment variables** are like named containers that store values your programs can read. Think of them as "global settings" that exist outside your code.

**Real-world analogy:** Imagine you have a note on your refrigerator that says "WiFi Password: MySecret123". Any family member can read that note without you having to tell them directly. Environment variables work the same way - they're values stored "outside" the application that the application can read when it needs them.

**Why use environment variables?**

1. **Security**: Sensitive values (passwords, API keys) aren't stored in code
2. **Flexibility**: Same code can run in different environments (development, production) with different settings
3. **Convenience**: Change settings without modifying and rebuilding code

### Example in Plain Terms

Without environment variables:

```java
// BAD: Password is hardcoded in the code
String databasePassword = "superSecret123";  // Anyone who sees the code sees the password!
```

With environment variables:

```java
// GOOD: Password comes from environment
String databasePassword = System.getenv("DB_PASSWORD");  // The actual password is NOT in the code
```

Then, when running the program, you set the value:

```bash
# On Mac/Linux
export DB_PASSWORD=superSecret123

# On Windows (Command Prompt)
set DB_PASSWORD=superSecret123
```

### How This Project Uses Environment Variables

In this project, environment variables are primarily set in `docker-compose.yml`:

```yaml
auth-service:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/auth_db
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: postgres
```

When the Docker container starts, these environment variables are automatically available inside the container. The Spring Boot application reads them and uses them for configuration.

---

## Spring Boot Configuration Deep Dive

Spring Boot has a sophisticated configuration system. This section explains how it works.

### What is Configuration?

**Configuration** is all the settings that control how your application behaves:

- What port to run on
- How to connect to the database
- What the JWT secret key is
- How much logging to show

### Configuration Sources (Priority Order)

Spring Boot reads configuration from multiple sources. If the same setting is defined in multiple places, the one with **higher priority wins**.

Here's the priority order (highest to lowest):

| Priority    | Source                    | Example                                |
| ----------- | ------------------------- | -------------------------------------- |
| 1 (Highest) | Command-line arguments    | `java -jar app.jar --server.port=9000` |
| 2           | Environment variables     | `export SERVER_PORT=9000`              |
| 3           | application-{profile}.yml | `application-docker.yml`               |
| 4 (Lowest)  | application.yml           | `application.yml`                      |

### How Spring Boot Translates Between Formats

Spring Boot is smart about property names. These are all equivalent:

| Format               | Example                       | Where Used            |
| -------------------- | ----------------------------- | --------------------- |
| YAML dot notation    | `spring.datasource.url`       | application.yml       |
| Environment variable | `SPRING_DATASOURCE_URL`       | Docker Compose, shell |
| Command line         | `--spring.datasource.url=...` | java -jar command     |

**The Rule**: To convert from YAML to environment variable:

1. Replace dots (`.`) with underscores (`_`)
2. Convert to UPPERCASE

Examples:

- `server.port` → `SERVER_PORT`
- `spring.datasource.url` → `SPRING_DATASOURCE_URL`
- `jwt.secret` → `JWT_SECRET`

### Configuration Files in This Project

```
auth-service/src/main/resources/
├── application.yml          # Default configuration (used when running locally)
└── application-docker.yml   # Docker profile configuration (used in containers)
```

### The application.yml File

This is the main configuration file. Here's the structure with explanations:

```yaml
# Server Configuration
server:
  port: 8081 # What port the service runs on

# Spring Configuration
spring:
  application:
    name: auth-service # Name of this service

  datasource: # Database connection settings
    url: jdbc:postgresql://localhost:5432/auth_db # Database URL
    username: postgres # Database username
    password: postgres # Database password

  jpa: # JPA/Hibernate settings
    hibernate:
      ddl-auto: update # Auto-create/update database tables
    show-sql: true # Show SQL queries in logs

# Custom Application Settings
jwt:
  secret: myVerySecretKey... # Secret key for JWT tokens
  expiration: 86400000 # Token expiration (24 hours in milliseconds)
```

### Reading Configuration Values in Code

Spring Boot provides the `@Value` annotation to read configuration values in your Java code:

```java
@Component
public class JwtUtil {

    // Reads "jwt.secret" from application.yml
    @Value("${jwt.secret}")
    private String secret;

    // Reads "jwt.expiration" with a default value of 86400000 if not set
    @Value("${jwt.expiration:86400000}")
    private Long expiration;
}
```

**How it works:**

1. Spring starts up and reads all configuration files
2. When creating the `JwtUtil` object, Spring sees `@Value("${jwt.secret}")`
3. Spring looks up `jwt.secret` from configuration
4. Spring injects that value into the `secret` field
5. Your code can now use `this.secret` to get the value

### Configuration Override Example

Let's trace how `spring.datasource.url` gets resolved:

**When running locally (no environment variable set):**

1. Spring checks for command-line argument → Not found
2. Spring checks for environment variable `SPRING_DATASOURCE_URL` → Not found
3. Spring checks for `application-{profile}.yml` → Not found (no profile active)
4. Spring reads `application.yml` → Found: `jdbc:postgresql://localhost:5432/auth_db`

**When running in Docker (environment variable set in docker-compose.yml):**

1. Spring checks for command-line argument → Not found
2. Spring checks for environment variable `SPRING_DATASOURCE_URL` → Found: `jdbc:postgresql://postgres:5432/auth_db`
3. Uses the environment variable value (higher priority!)

---

## Project Environment Variables Reference

This section lists every environment variable used in this project, where it's defined, and what it does.

### PostgreSQL Service

| Variable                      | Default Value        | Where Defined      | Description                    |
| ----------------------------- | -------------------- | ------------------ | ------------------------------ |
| `POSTGRES_USER`               | `postgres`           | docker-compose.yml | PostgreSQL superuser username  |
| `POSTGRES_PASSWORD`           | `postgres`           | docker-compose.yml | PostgreSQL superuser password  |
| `POSTGRES_MULTIPLE_DATABASES` | `auth_db,account_db` | docker-compose.yml | Databases to create on startup |

### Auth Service

| Variable                     | Default Value                              | Where Defined                        | Description                                           |
| ---------------------------- | ------------------------------------------ | ------------------------------------ | ----------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`     | (none)                                     | docker-compose.yml                   | Active Spring profile (set to `docker` in containers) |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/auth_db` | docker-compose.yml / application.yml | Database connection URL                               |
| `SPRING_DATASOURCE_USERNAME` | `postgres`                                 | docker-compose.yml / application.yml | Database username                                     |
| `SPRING_DATASOURCE_PASSWORD` | `postgres`                                 | docker-compose.yml / application.yml | Database password                                     |
| `JWT_SECRET`                 | (in application.yml)                       | application.yml                      | Secret key for signing JWT tokens                     |
| `JWT_EXPIRATION`             | `86400000`                                 | application.yml                      | Token expiration time in milliseconds (24 hours)      |

### Account Service

| Variable                     | Default Value                                 | Where Defined                        | Description                      |
| ---------------------------- | --------------------------------------------- | ------------------------------------ | -------------------------------- |
| `SPRING_PROFILES_ACTIVE`     | (none)                                        | docker-compose.yml                   | Active Spring profile            |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/account_db` | docker-compose.yml / application.yml | Database connection URL          |
| `SPRING_DATASOURCE_USERNAME` | `postgres`                                    | docker-compose.yml / application.yml | Database username                |
| `SPRING_DATASOURCE_PASSWORD` | `postgres`                                    | docker-compose.yml / application.yml | Database password                |
| `EXCHANGE_SERVICE_URL`       | `http://localhost:8084`                       | docker-compose.yml / application.yml | URL of the mock exchange service |

### Gateway Service

| Variable                 | Default Value           | Where Defined      | Description                |
| ------------------------ | ----------------------- | ------------------ | -------------------------- |
| `SPRING_PROFILES_ACTIVE` | (none)                  | docker-compose.yml | Active Spring profile      |
| `AUTH_SERVICE_URL`       | `http://localhost:8081` | docker-compose.yml | URL of the auth service    |
| `ACCOUNT_SERVICE_URL`    | `http://localhost:8082` | docker-compose.yml | URL of the account service |

### Mock Exchange Service

| Variable                 | Default Value | Where Defined      | Description           |
| ------------------------ | ------------- | ------------------ | --------------------- |
| `SPRING_PROFILES_ACTIVE` | (none)        | docker-compose.yml | Active Spring profile |

### How to Override Environment Variables

#### In Docker Compose

Modify the `environment` section in `docker-compose.yml`:

```yaml
auth-service:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://my-database:5432/mydb
    JWT_SECRET: my-custom-secret-key
```

#### When Running Locally (Mac/Linux)

```bash
# Set before running
export JWT_SECRET=my-custom-secret
./mvnw spring-boot:run -pl auth-service

# Or inline
JWT_SECRET=my-custom-secret ./mvnw spring-boot:run -pl auth-service
```

#### When Running Locally (Windows)

```cmd
set JWT_SECRET=my-custom-secret
mvnw.cmd spring-boot:run -pl auth-service
```

---

## Spring Profiles Explained

### What Are Spring Profiles?

**Spring Profiles** let you have different configurations for different situations (environments).

**Real-world analogy:** Think of it like having different outfits for different occasions:

- **Work outfit** - Professional clothes for the office
- **Gym outfit** - Sportswear for exercise
- **Casual outfit** - Comfortable clothes for weekends

Similarly, your application might need different "outfits" (configurations):

- **default profile** - When running on your laptop for development
- **docker profile** - When running in Docker containers
- **production profile** - When running on live servers

### How Profiles Work in This Project

When running in Docker, `docker-compose.yml` sets:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
```

This tells Spring Boot: "Use the `docker` profile".

Spring then looks for a file named `application-docker.yml` and loads those settings **on top of** the base `application.yml`.

### Profile Configuration Files

| Profile        | Configuration File       | When Used                                     |
| -------------- | ------------------------ | --------------------------------------------- |
| (none/default) | `application.yml`        | Running locally with `./mvnw spring-boot:run` |
| `docker`       | `application-docker.yml` | Running in Docker containers                  |

### Example: Gateway Service Routing

**application.yml** (default - for local development):

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:8081 # Points to localhost
          predicates:
            - Path=/api/v1/auth/**
```

**application-docker.yml** (docker profile):

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:8081 # Points to Docker service name
          predicates:
            - Path=/api/v1/auth/**
```

**Why the difference?**

When running locally:

- Services run on your computer (`localhost`)
- auth-service is at `localhost:8081`

When running in Docker:

- Each service is in its own container
- Containers find each other by **service name** (defined in docker-compose.yml)
- auth-service is at `auth-service:8081` (not localhost!)

### Activating Profiles

**Via environment variable** (most common in Docker):

```bash
export SPRING_PROFILES_ACTIVE=docker
```

**Via command line argument**:

```bash
java -jar app.jar --spring.profiles.active=docker
```

**Via Maven when running locally**:

```bash
./mvnw spring-boot:run -pl gateway -Dspring-boot.run.profiles=docker
```

### Multiple Profiles

You can activate multiple profiles at once:

```bash
export SPRING_PROFILES_ACTIVE=docker,debug
```

Spring will load:

1. `application.yml` (base)
2. `application-docker.yml` (docker profile)
3. `application-debug.yml` (debug profile, if it exists)

Later files override earlier ones.

---

## Port Configuration Reference

### Service Ports

| Service             | Port | URL                   | Description                           |
| ------------------- | ---- | --------------------- | ------------------------------------- |
| **Gateway**         | 8080 | http://localhost:8080 | Main entry point for all API requests |
| **Auth Service**    | 8081 | http://localhost:8081 | User registration and login           |
| **Account Service** | 8082 | http://localhost:8082 | Account and balance management        |
| **Mock Exchange**   | 8084 | http://localhost:8084 | Currency exchange simulation          |
| **PostgreSQL**      | 5432 | localhost:5432        | Database server                       |

### API Endpoints

#### Via Gateway (Recommended)

| Endpoint                  | Method | Description                  |
| ------------------------- | ------ | ---------------------------- |
| `/api/v1/auth/register`   | POST   | Register a new user          |
| `/api/v1/auth/login`      | POST   | Login and get JWT token      |
| `/api/v1/auth/health`     | GET    | Auth service health check    |
| `/api/v1/accounts`        | POST   | Create a new account         |
| `/api/v1/accounts/{id}`   | GET    | Get account details          |
| `/api/v1/accounts/health` | GET    | Account service health check |

#### Mock Exchange (Direct)

| Endpoint       | Method | Description                   |
| -------------- | ------ | ----------------------------- |
| `/api/quote`   | GET    | Get exchange rate quote       |
| `/api/execute` | POST   | Execute a currency exchange   |
| `/api/health`  | GET    | Exchange service health check |

### Service Dependencies (Startup Order)

```
PostgreSQL (must start first)
    ↓
┌───────────────┐
│ mock-exchange │  (no database needed)
└───────────────┘
    ↓
┌───────────────┐    ┌─────────────────┐
│ auth-service  │    │ account-service │  (both need PostgreSQL)
└───────────────┘    └─────────────────┘
         ↓                    ↓
         └──────────┬─────────┘
                    ↓
              ┌─────────┐
              │ gateway │  (needs auth & account services)
              └─────────┘
```

Docker Compose handles this automatically using `depends_on` with health checks.

---

## Troubleshooting

This section covers common issues and their solutions.

### Build Issues

#### "Java version mismatch" or "Unsupported class file major version"

**Problem**: You have the wrong Java version installed.

**Solution**:

```bash
# Check your Java version
java -version

# Should show: openjdk version "21.x.x"
# If not, install Java 21 (see Prerequisites section)
```

#### "mvnw: Permission denied"

**Problem**: The Maven wrapper script isn't executable.

**Solution**:

```bash
chmod +x mvnw
```

#### "Connection refused" during build

**Problem**: Maven can't download dependencies.

**Solution**: Check your internet connection and proxy settings.

### Docker Issues

#### "Cannot connect to Docker daemon"

**Problem**: Docker isn't running.

**Solution**:

- **Mac/Windows**: Open Docker Desktop application
- **Linux**: `sudo systemctl start docker`

#### "Port already in use"

**Problem**: Another application is using the required port.

**Solution**:

```bash
# Find what's using the port (example: port 8080)
lsof -i :8080  # Mac/Linux
netstat -ano | findstr :8080  # Windows

# Stop the other application, or change the port in docker-compose.yml
```

#### Services keep restarting

**Problem**: A service is crashing on startup.

**Solution**:

```bash
# Check the logs
docker-compose logs auth-service

# Common causes:
# - Database not ready (usually resolves after a few seconds)
# - Environment variables missing
# - Port conflicts
```

### Database Issues

#### "Connection refused to PostgreSQL"

**Problem**: PostgreSQL container isn't running or isn't ready.

**Solution**:

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# If not healthy, restart it
docker-compose restart postgres

# Check PostgreSQL logs
docker-compose logs postgres
```

#### "Database 'auth_db' does not exist"

**Problem**: The initialization script didn't create the databases.

**Solution**:

```bash
# Stop everything and remove volumes
docker-compose down -v

# Start fresh
docker-compose up -d --build
```

### Service Communication Issues

#### Gateway returns 502 Bad Gateway

**Problem**: Gateway can't reach backend services.

**Solution**:

1. Check if backend services are running: `docker-compose ps`
2. Check backend service logs: `docker-compose logs auth-service`
3. Verify the `docker` profile is active (should route to service names, not localhost)

#### "Connection refused" between services

**Problem**: Services are trying to use `localhost` instead of Docker service names.

**Solution**:

1. Ensure `SPRING_PROFILES_ACTIVE=docker` is set in docker-compose.yml
2. Check that `application-docker.yml` exists and has correct service URLs

### Health Check Failures

#### Service shows "unhealthy" status

**Problem**: The health check endpoint is failing.

**Solution**:

```bash
# Test the health endpoint directly
docker exec crossborder-auth-service curl -f http://localhost:8081/api/v1/auth/health

# If curl isn't available in the container, check the logs
docker-compose logs auth-service
```

### Complete Reset

If nothing works, try a complete reset:

```bash
# Stop and remove everything
docker-compose down -v

# Remove all related images
docker rmi $(docker images -q "crashcourse*")

# Clear Maven cache (optional, takes longer to rebuild)
rm -rf ~/.m2/repository

# Rebuild from scratch
./mvnw clean install -DskipTests

# Start fresh
docker-compose up -d --build
```

---

## Quick Reference Cheatsheet

### Essential Commands

```bash
# Build everything
./mvnw clean install -DskipTests

# Start all services
docker-compose up -d --build

# Check status
docker-compose ps

# View logs
docker-compose logs -f

# Stop everything
docker-compose down

# Complete reset
docker-compose down -v && docker-compose up -d --build
```

### Test Endpoints

```bash
# Health checks
curl http://localhost:8080/api/v1/auth/health

# Register user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}'
```

---

**Congratulations!** You now have everything you need to build, run, and understand this microservices project. If you have questions, check the other documentation files in the `docs/` folder for more detailed explanations of specific concepts.
