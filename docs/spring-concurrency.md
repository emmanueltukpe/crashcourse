# Spring Concurrency - Asynchronous Processing in Spring Boot

A practical guide to building concurrent Spring Boot applications using `@Async`, thread pools, and modern reactive patterns.

## Table of Contents

1. [Why Async in Spring?](#why-async-in-spring)
2. [@Async Basics](#async-basics)
3. [Thread Pool Configuration](#thread-pool-configuration)
4. [CompletableFuture](#completablefuture)
5. [Error Handling](#error-handling)
6. [Best Practices](#best-practices)

---

## Why Async in Spring?

### The Problem: Blocking Operations

```java
@RestController
public class UserController {
    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        User user = userService.findById(id);      // DB query: 50ms
        Profile profile = profileService.get(id);   // API call: 200ms
        Stats stats = statsService.calculate(id);   // Computation: 100ms

        // Total time: 350ms
        return new UserWithDetails(user, profile, stats);
    }
}
```

**Problem**: Operations run sequentially. Total time = 350ms.

### The Solution: Run in Parallel

```java
@RestController
public class UserController {
    @GetMapping("/user/{id}")
    public CompletableFuture<UserWithDetails> getUser(@PathVariable  Long id) {
        // All three run in parallel!
        CompletableFuture<User> userFuture = userService.findByIdAsync(id);
        CompletableFuture<Profile> profileFuture = profileService.getAsync(id);
        CompletableFuture<Stats> statsFuture = statsService.calculateAsync(id);

        // Combine results
        return CompletableFuture.allOf(userFuture, profileFuture, statsFuture)
            .thenApply(v -> new UserWithDetails(
                userFuture.join(),
                profileFuture.join(),
                statsFuture.join()
            ));

        // Total time: max(50ms, 200ms, 100ms) = 200ms ← 43% faster!
    }
}
```

---

## @Async Basics

### Enable Async Support

```java
@Configuration
@EnableAsync // ← Add this annotation
public class AsyncConfiguration {
    // Config goes here
}
```

### Create Async Methods

```java
@Service
public class EmailService {

    // Synchronous (blocks caller)
    public void sendEmail(String to, String subject, String body) {
        // Send email (takes 2 seconds)
    }

    // Asynchronous (returns immediately)
    @Async
    public void sendEmailAsync(String to, String subject, String body) {
        // Runs in separate thread
        // Caller doesn't wait
    }
}
```

**Usage:**

```java
@RestController
public class OrderController {
    @Autowired
    private EmailService emailService;

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.create(request);

        // Send confirmation email asynchronously
        emailService.sendEmailAsync(
            request.getEmail(),
            "Order Confirmation",
            "Your order #" + order.getId() + " is confirmed"
        );
        // Method returns immediately, email sent in background

        return new OrderResponse(order.getId(), "Order created");
    }
}
```

### @Async with Return Values

```java
@Service
public class DataService {

    // Return CompletableFuture for async results
    @Async
    public CompletableFuture<List<Product>> fetchProducts() {
        // Simulate slow API call
        Thread.sleep(2000);
        List<Product> products = externalApi.getProducts();
        return CompletableFuture.completedFuture(products);
    }

    @Async
    public CompletableFuture<List<Review>> fetchReviews() {
        Thread.sleep(1500);
        List<Review> reviews = externalApi.getReviews();
        return CompletableFuture.completedFuture(reviews);
    }
}
```

**Usage:**

```java
@GetMapping("/dashboard")
public DashboardData getDashboard() throws Exception {
    // Both calls start immediately
    CompletableFuture<List<Product>> productsFuture = dataService.fetchProducts();
    CompletableFuture<List<Review>> reviewsFuture = dataService.fetchReviews();

    // Wait for both to complete
    CompletableFuture.allOf(productsFuture, reviewsFuture).join();

    // Get results
    return new DashboardData(
        productsFuture.get(),
        reviewsFuture.get()
    );
    // Time: 2 seconds (not 3.5!)
}
```

### Important @Async Rules

**❌ Won't work:**

```java
@Service
public class MyService {
    @Async
    public void asyncMethod() {
        // ...
    }

    public void callAsync() {
        this.asyncMethod(); // ← Won't be async! Called directly, not via proxy
    }
}
```

**✓ Will work:**

```java
@Service
public class CallerService {
    @Autowired
    private MyService myService; // ← Spring proxy

    public void callAsync() {
        myService.asyncMethod(); // ← Async! Called via Spring proxy
    }
}
```

**Rules:**

1. Must call `@Async` method from **different class** (Spring needs proxy)
2. Method must be **public**
3. `@EnableAsync` must be present
4. Cannot call async method from same class using `this`

---

## Thread Pool Configuration

### Default Thread Pool

By default, Spring uses `SimpleAsyncTaskExecutor` which creates a NEW thread for every task. **Not good for production!**

### Custom Thread Pool

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: Always keep this many threads alive
        executor.setCorePoolSize(5);

        // Max pool size: Create up to this many threads total
        executor.setMaxPoolSize(10);

        // Queue capacity: Queue tasks if all threads busy
        executor.setQueueCapacity(100);

        // Thread name prefix (for debugging)
        executor.setThreadNamePrefix("async-");

        // Rejection policy: What to do if queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
```

**How it works:**

```
Tasks arrive:
                   Core Pool (5 threads)
Task 1-5      →    [T1][T2][T3][T4][T5]     ← Processed immediately

Task 6-105    →    Queue (100 capacity)     ← Waiting

Task 106-110  →    Extra threads created    ← Up to maxPoolSize
                   [T6][T7][T8][T9][T10]

Task 111+     →    Rejection policy         ← Queue full, max threads reached
```

**Rejection Policies:**

```java
// 1. CallerRunsPolicy: Caller thread runs task (slows down caller)
new ThreadPoolExecutor.CallerRunsPolicy()

// 2. AbortPolicy: Throw RejectedExecutionException (default)
new ThreadPoolExecutor.AbortPolicy()

// 3. DiscardPolicy: Silently discard task
new ThreadPoolExecutor.DiscardPolicy()

// 4. DiscardOldestPolicy: Discard oldest queued task
new ThreadPoolExecutor.DiscardOldestPolicy()
```

### Multiple Thread Pools

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {

    // For I/O-bound tasks (network calls, file I/O)
    @Bean(name = "ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);  // More threads for I/O
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("io-");
        executor.initialize();
        return executor;
    }

    // For CPU-bound tasks (calculations, data processing)
    @Bean(name = "cpuTaskExecutor")
    public Executor cpuTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cores);     // # of cores
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-");
        executor.initialize();
        return executor;
    }
}
```

**Usage:**

```java
@Service
public class ReportService {

    @Async("ioTaskExecutor") // ← Specify executor
    public CompletableFuture<Data> fetchDataFromApi() {
        // I/O operation
    }

    @Async("cpuTaskExecutor") // ← Different executor
    public CompletableFuture<Report> generateReport(Data data) {
        // CPU-intensive calculation
    }
}
```

### Tuning Guidelines

**For I/O-bound tasks** (network, database):

```java
corePoolSize = 2 * CPU_CORES  // Many threads OK (they mostly wait)
maxPoolSize = 4 * CPU_CORES
```

**For CPU-bound tasks** (calculations):

```java
corePoolSize = CPU_CORES      // More threads = more context switching
maxPoolSize = CPU_CORES + 1
```

**Queue capacity:**

```java
queueCapacity = maxPoolSize * 10  // Rule of thumb
```

---

## CompletableFuture

### Basics

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        // Run in separate thread
        return "Hello";
    });

// Get result (blocks)
String result = future.get();
```

### Chaining Operations

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        return "Hello";
    })
    .thenApply(s -> s + " World")      // Transform result
    .thenApply(String::toUpperCase);   // Transform again

future.get(); // "HELLO WORLD"
```

### Combining Futures

```java
CompletableFuture<User> userFuture = userService.findByIdAsync(1L);
CompletableFuture<List<Order>> ordersFuture = orderService.findByUserIdAsync(1L);

// Combine both results
CompletableFuture<UserWithOrders> combined = userFuture
    .thenCombine(ordersFuture, (user, orders) -> {
        return new UserWithOrders(user, orders);
    });
```

### Running Multiple Tasks

```java
// Run all, wait for all to complete
CompletableFuture<Void> allOf = CompletableFuture.allOf(
    sendEmail(),
    updateDatabase(),
    logEvent()
);
allOf.join(); // Wait for all

// Run all, return when FIRST completes
CompletableFuture<Object> anyOf = CompletableFuture.anyOf(
    fetchFromCache(),
    fetchFromPrimaryDB(),
    fetchFromReplicaDB()
);
Object firstResult = anyOf.join(); // First to finish wins!
```

### Exception Handling

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> {
        if (Math.random() > 0.5) {
            throw new RuntimeException("Random failure!");
        }
        return "Success";
    })
    .exceptionally(ex -> {
        // Handle exception
        logger.error("Task failed", ex);
        return "Default value";
    });
```

### Complete Example: Parallel Data Aggregation

```java
@Service
public class DashboardService {

    @Async
    public CompletableFuture<UserStats> getUserStats(Long userId) {
        // Simulate slow query
        Thread.sleep(500);
        return CompletableFuture.completedFuture(new UserStats(...));
    }

    @Async
    public CompletableFuture<List<Transaction>> getRecentTransactions(Long userId) {
        Thread.sleep(300);
        return CompletableFuture.completedFuture(transactions);
    }

    @Async
    public CompletableFuture<BigDecimal> getAccountBalance(Long userId) {
        Thread.sleep(200);
        return CompletableFuture.completedFuture(balance);
    }

    public Dashboard buildDashboard(Long userId) throws Exception {
        long start = System.currentTimeMillis();

        // Kick off all three tasks
        CompletableFuture<UserStats> statsFuture = getUserStats(userId);
        CompletableFuture<List<Transaction>> txnsFuture = getRecentTransactions(userId);
        CompletableFuture<BigDecimal> balanceFuture = getAccountBalance(userId);

        // Wait for all to complete
        CompletableFuture.allOf(statsFuture, txnsFuture, balanceFuture).join();

        // Build dashboard
        Dashboard dashboard = new Dashboard(
            statsFuture.get(),
            txnsFuture.get(),
            balanceFuture.get()
        );

        long duration = System.currentTimeMillis() - start;
        System.out.println("Dashboard built in " + duration + "ms");
        // Sequential: 1000ms, Parallel: 500ms!

        return dashboard;
    }
}
```

---

## Error Handling

### In @Async Methods

```java
@Service
public class NotificationService {

    @Async
    public CompletableFuture<String> sendNotification(String userId) {
        try {
            // Send notification
            return CompletableFuture.completedFuture("Sent");
        } catch (Exception e) {
            // Log error
            logger.error("Failed to send notification", e);
            // Return exceptional future
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

### Custom Exception Handler

```java
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }
}

public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        logger.error("Async method {} threw exception", method.getName(), throwable);
        logger.error("Parameters: {}", Arrays.toString(params));

        // Send alert, save to error log, etc.
    }
}
```

---

## Best Practices

### 1. Choose the Right Thread Pool

```java
// Good: Separate pools for different workloads
@Async("ioExecutor")    // I/O tasks
public CompletableFuture<Data> fetchData() { }

@Async("cpuExecutor")   // CPU tasks
public CompletableFuture<Report> processData() { }
```

### 2. Set Timeouts

```java
CompletableFuture<String> future = service.slowOperation();

try {
    String result = future.get(5, TimeUnit.SECONDS); // 5 second timeout
} catch (TimeoutException e) {
    // Handle timeout
    future.cancel(true); // Cancel the task
}
```

### 3. Monitor Thread Pools

```java
@Component
public class ThreadPoolMonitor {

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Scheduled(fixedRate = 60000) // Every minute
    public void logThreadPoolStats() {
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();

        logger.info("Thread Pool Stats:");
        logger.info("- Active threads: {}", threadPool.getActiveCount());
        logger.info("- Pool size: {}", threadPool.getPoolSize());
        logger.info("- Queue size: {}", threadPool.getQueue().size());
        logger.info("- Completed tasks: {}", threadPool.getCompletedTaskCount());
    }
}
```

### 4. Graceful Shutdown

```java
@Configuration
public class AsyncConfiguration {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // ... configuration ...

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Max time to wait
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
```

### 5. Avoid Long-Running @Async Methods

```java
// Bad: Blocks thread for hours
@Async
public void processLargeFile(File file) {
    // Process for 2 hours...
}

// Good: Break into smaller tasks
public void processLargeFile(File file) {
    List<Chunk> chunks = splitFile(file);
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Chunk chunk : chunks) {
        futures.add(processChunkAsync(chunk));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}

@Async
private CompletableFuture<Void> processChunkAsync(Chunk chunk) {
    // Process small chunk (minutes, not hours)
}
```

### 6. Use @Transactional Carefully with @Async

```java
// Won't work as expected!
@Transactional
@Async
public void saveData(Data data) {
    // Transaction won't propagate to async thread
}

// Better: Start transaction inside async method
@Async
public void saveData(Data data) {
    saveDataTransactional(data);
}

@Transactional
public void saveDataTransactional(Data data) {
    repository.save(data);
}
```

---

## Summary

**Key Takeaways:**

✅ **@Async**: Run methods asynchronously in separate threads  
✅ **CompletableFuture**: Get results from async operations  
✅ **Thread Pools**: Reuse threads, don't create new ones  
✅ **I/O vs CPU**: Different thread pool sizing strategies  
✅ **Parallel Execution**: Combine CompletableFutures for big speedups  
✅ **Error Handling**: Always handle exceptions in async code  
✅ **Monitoring**: Track thread pool health

**When to Use Async:**

- External API calls (network I/O)
- Database queries that can run in parallel
- File operations
- Email/notification sending
- Background processing

**When NOT to Use Async:**

- Simple, fast operations (overhead not worth it)
- Operations that must complete before returning
- When caller needs immediate result

**Next Steps:**

- Read [java-concurrency.md](java-concurrency.md) for Java concurrency basics
- Read [locking-and-isolation.md](locking-and-isolation.md) voor database concurrency
- Study our concurrency tests for real examples

Async programming makes applications fast and responsive! 🚀
