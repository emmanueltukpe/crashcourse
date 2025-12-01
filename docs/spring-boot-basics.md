# Spring Boot Basics - Getting Started with Spring

Spring Boot is a framework that makes it easy to create stand-alone, production-grade Spring applications. This guide covers the core concepts you need to understand our payment simulator.

## Table of Contents

1. [What is Spring Boot?](#what-is-spring-boot)
2. [Dependency Injection (DI) and IoC](#dependency-injection-and-ioc)
3. [Core Annotations](#core-annotations)
4. [Application Layers](#application-layers)
5. [Configuration with application.yml](#configuration)
6. [Spring Boot Magic](#spring-boot-magic)

---

## What is Spring Boot?

**Spring Boot** = Spring Framework + Embedded Server + Auto-configuration + Production-ready features

**Why use Spring Boot?**

- ✅ **Rapid development**: Less boilerplate, more productivity
- ✅ **Convention over configuration**: Sensible defaults
- ✅ **Embedded server**: No need to deploy WARs to Tomcat
- ✅ **Microservices-friendly**: Perfect for building REST APIs
- ✅ **Production-ready**: Metrics, health checks built-in

**Traditional Java vs Spring Boot:**

```java
// Traditional: You manage everything
public class Main {
    public static void main(String[] args) {
        Server server = new Server(8080);
        Servlet servlet = new MyServlet();
        // Configure database connection
        // Configure JSON parsing
        // Configure security
        // ... hundreds of lines of config
        server.start();
    }
}

// Spring Boot: Framework handles the details
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        // That's it! Spring Boot does the rest
    }
}
```

---

## Dependency Injection and IoC

### What is Dependency Injection?

**Dependency Injection (DI)** means Spring creates and provides objects your code needs, rather than you creating them yourself.

**Without DI (manual):**

```java
public class UserService {
    private UserRepository repository;

    public UserService() {
        // You create dependencies manually
        this.repository = new UserRepository();
    }
}
```

**With DI (Spring):**

```java
@Service
public class UserService {
    private final UserRepository repository;

    // Spring automatically provides UserRepository
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
}
```

### IoC (Inversion of Control)

**Normal control flow**: Your code calls the framework
**Inverted control**: The framework calls your code

With Spring:

1. Spring creates objects (called "beans")
2. Spring manages their lifecycle
3. Spring injects dependencies where needed
4. Your code just uses them!

### The Spring Container (Application Context)

Think of the Spring container as a factory that creates and manages all your objects:

```
Your Application Starts
         ↓
Spring scans for @Component, @Service, @Repository, @Controller
         ↓
Spring creates instances (beans)
         ↓
Spring injects dependencies
         ↓
Your application runs
```

---

## Core Annotations

### @SpringBootApplication

The main annotation that combines three important annotations:

```java
@SpringBootApplication  // Includes @Configuration + @EnableAutoConfiguration + @ComponentScan
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Component Annotations

These tell Spring "create and manage this class as a bean":

```java
@Component  // Generic component
public class MyComponent { }

@Service  // Business logic layer
public class UserService { }

@Repository  // Data access layer
public class UserRepository { }

@Controller / @RestController  // Web layer
public class AuthController { }

@Configuration  // Configuration class
public class AppConfig { }
```

**Why different annotations if they all do the same thing?**

- Code organization and clarity
- Some provide additional features (@Repository adds database exception translation)
- Easier to understand the purpose of each class

### Injection Annotations

```java
// Constructor injection (RECOMMENDED)
@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }
}

// Field injection (NOT RECOMMENDED - harder to test)
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
}

// Setter injection
@Service
public class UserService {
    private UserRepository repository;

    @Autowired
    public void setRepository(UserRepository repository) {
        this.repository = repository;
    }
}
```

### HTTP Mapping Annotations

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")  // GET /api/users/123
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping  // POST /api/users
    public User createUser(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")  // PUT /api/users/123
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")  // DELETE /api/users/123
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

### Parameter Annotations

```java
@GetMapping("/search")
public List<User> search(
    @RequestParam String name,         // ?name=Alice
    @RequestParam(required = false) Integer age,  // ?age=25 (optional)
    @RequestHeader("User-Agent") String userAgent  // From HTTP header
) {
    return userService.search(name, age);
}
```

---

## Application Layers

Spring applications typically use a layered architecture:

```
┌─────────────────────────────────────┐
│   Controller (Web Layer)            │  @RestController
│   - Handles HTTP requests/responses │  - Thin layer
│   - Validates input                 │  - No business logic
└────────────┬────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│   Service (Business Logic Layer)    │  @Service
│   - Core business logic             │  - @Transactional
│   - Coordinates multiple repos      │  - Reusable
└────────────┬────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│   Repository (Data Access Layer)    │  @Repository
│   - Database operations             │  - JPA/JDBC
│   - CRUD operations                 │  - Extended from JpaRepository
└─────────────────────────────────────┘
```

**Example flow**:

```
Client
  ↓ HTTP POST /api/users
AuthController
  ↓ authService.register(request)
AuthService
  ↓ userRepository.save(user)
UserRepository
  ↓ SQL INSERT
Database
```

---

## Configuration

### application.yml Structure

```yaml
# Server configuration
server:
  port: 8081

# Spring configuration
spring:
  application:
    name: my-service

  # Database
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: postgres
    password: secret

  # JPA/Hibernate
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

# Custom properties
myapp:
  jwt-secret: mySecret123
  token-expiry: 3600
```

### Reading Config Values

```java
@Component
public class JwtUtil {

    @Value("${myapp.jwt-secret}")
    private String secret;

    @Value("${myapp.token-expiry}")
    private Integer expiry;
}
```

### Profiles

Different configurations for different environments:

```yaml
# application.yml (default)
server:
  port: 8080

---
# application-dev.yml (development)
logging:
  level:
    root: DEBUG

---
# application-prod.yml (production)
logging:
  level:
    root: WARN
```

Activate with: `java -jar app.jar --spring.profiles.active=prod`

---

## Spring Boot Magic

### Auto-Configuration

Spring Boot automatically configures your application based on dependencies:

**You add to pom.xml:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Spring Boot automatically:**

- Configures a DataSource
- Sets up EntityManager
- Enables JPA repositories
- Configures Hibernate
- Sets up transaction management

### Embedded Server

No need for external Tomcat! Spring Boot includes a web server:

```bash
# Run the JAR - server starts automatically
java -jar myapp.jar

# Server is running on http://localhost:8080
```

### Health Checks and Metrics

Spring Boot Actuator provides production-ready features:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Endpoints:

- `/actuator/health` - Is the app running?
- `/actuator/metrics` - Performance metrics
- `/actuator/info` - Application info

---

## Common Patterns

### RESTful CRUD Service

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Product create(@Valid @RequestBody Product product) {
        return productService.create(product);
    }
}
```

### Service with Transaction

```java
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Product create(Product product) {
        // Validate
        // Save to database
        return repository.save(product);
    }
}
```

---

## Quick Reference

```java
// Main application
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}

// Controller
@RestController
@RequestMapping("/api/users")
public class UserController { }

// Service
@Service
public class UserService { }

// Repository
public interface UserRepository extends JpaRepository<User, Long> { }

// Configuration value
@Value("${app.name}")
private String appName;
```

---

## Next Steps

Now that you understand Spring Boot, learn about [Transactional Basics](./transactional-basics.md) to understand how databases transactions work in Spring!
