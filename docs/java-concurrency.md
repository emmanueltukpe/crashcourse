# Java Concurrency - From Basics to Production

A comprehensive guide to concurrent programming in Java, from first principles to real-world applications.

## Table of Contents

1. [What is Concurrency?](#what-is-concurrency)
2. [Threads in Java](#threads-in-java)
3. [Thread Safety Problems](#thread-safety-problems)
4. [Synchronization Mechanisms](#synchronization-mechanisms)
5. [Executor Framework](#executor-framework)
6. [Common Concurrency Patterns](#common-concurrency-patterns)
7. [Best Practices](#best-practices)

---

## What is Concurrency?

**Concurrency** means multiple things happening at the same time (or appearing to).

### Real-World Analogy

Imagine a bank with 3 tellers:

```
Customer 1 → Teller A (deposit)
Customer 2 → Teller B (withdrawal)    ← All happening simultaneously
Customer 3 → Teller C (balance check)
```

Without concurrency:

```
Customer 1 → Teller (deposit)    ← Must wait
Customer 2 waits...
Customer 3 waits...
```

### Why Use Concurrency?

**1. Performance**

- Utilize multiple CPU cores
- Handle more requests simultaneously
- Reduce response times

**2. Responsiveness**

- Keep UI responsive while processing
- Handle I/O operations in background
- Don't block user interactions

**3. Resource Utilization**

- CPU-bound tasks: More threads than cores (some benefit)
- I/O-bound tasks: Many threads (waiting for network/disk)

### Concurrency vs Parallelism

| Concurrency                      | Parallelism               |
| -------------------------------- | ------------------------- |
| **Structure** of code            | **Execution** of code     |
| Dealing with many things at once | Doing many things at once |
| Single core can handle           | Requires multiple cores   |
| Task switching rapidly           | Tasks truly simultaneous  |

```
Concurrency (1 CPU):
Thread 1: ████░░░░░████░░░░
Thread 2: ░░░░████░░░░████
         (Interleaved - context switching)

Parallelism (2 CPUs):
Thread 1: ████████████████
Thread 2: ████████████████
         (Truly simultaneous)
```

---

## Threads in Java

### What is a Thread?

A **thread** is a lightweight process - the smallest unit of execution.

```java
Process (Your Java Application)
├─ Main Thread (started automatically)
├─ Thread 1 (you create)
├─ Thread 2 (you create)
└─ Thread 3 (you create)
```

Every Java application has at least ONE thread: the `main` thread.

### Creating Threads

#### Method 1: Extend Thread Class

```java
public class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Thread running: " + Thread.currentThread().getName());
        // Do work here
    }
}

// Usage
MyThread thread = new MyThread();
thread.start(); // Starts new thread, calls run()
```

#### Method 2: Implement Runnable (Preferred)

```java
public class MyTask implements Runnable {
    @Override
    public void run() {
        System.out.println("Task running in: " + Thread.currentThread().getName());
        // Do work here
    }
}

// Usage
Thread thread = new Thread(new MyTask());
thread.start();
```

#### Method 3: Lambda Expression (Modern Java)

```java
Thread thread = new Thread(() -> {
    System.out.println("Lambda thread: " + Thread.currentThread().getName());
    // Do work here
});
thread.start();
```

**Why Runnable is better:**

- Can implement multiple interfaces (Java has single inheritance)
- Separates task from execution mechanism
- More flexible (can pass to ExecutorService, etc.)

### Thread Lifecycle

```
            start()
     NEW ──────────→ RUNNABLE ←──────┐
                        │             │
                        │ CPU         │ notify()
                        │ scheduler   │ wait() ends
                        ↓             │
                    RUNNING ──────────┤
                        │             │
                        │ sleep()     │
                        │ wait()      │
                        │ I/O block   │
                        ↓             │
                    BLOCKED/WAITING ──┘
                        │
                        │ run() completes
                        ↓
                    TERMINATED
```

**States explained:**

- **NEW**: Thread created but not started
- **RUNNABLE**: Ready to run, waiting for CPU
- **RUNNING**: Currently executing (CPU assigned)
- **BLOCKED/WAITING**: Waiting for lock, I/O, or sleep
- **TERMINATED**: Finished execution

### Basic Thread Operations

```java
// Sleep: Pause current thread
Thread.sleep(1000); // Sleep for 1 second (1000ms)

// Join: Wait for thread to finish
Thread thread = new Thread(() -> {
    // Do long task
});
thread.start();
thread.join(); // Wait for thread to complete before continuing

// Interrupt: Signal thread to stop
thread.interrupt();

// Check if interrupted
if (Thread.interrupted()) {
    // Clean up and exit
}

// Get current thread
Thread current = Thread.currentThread();
System.out.println("Running in: " + current.getName());
```

### Example: Download Files Concurrently

```java
public class FileDownloader {
    public static void main(String[] args) throws InterruptedException {
        String[] urls = {
            "https://example.com/file1.txt",
            "https://example.com/file2.txt",
            "https://example.com/file3.txt"
        };

        // Create thread for each download
        List<Thread> threads = new ArrayList<>();
        for (String url : urls) {
            Thread thread = new Thread(() -> {
                downloadFile(url);
            });
            thread.start();
            threads.add(thread);
        }

        // Wait for all downloads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("All downloads complete!");
    }

    private static void downloadFile(String url) {
        System.out.println("Downloading: " + url + " in " + Thread.currentThread().getName());
        // Simulate download
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Finished: " + url);
    }
}
```

---

## Thread Safety Problems

### Problem 1: Race Conditions

**Race condition**: Multiple threads access shared data, and outcome depends on execution timing.

```java
public class BankAccount {
    private int balance = 1000;

    public void withdraw(int amount) {
        if (balance >= amount) {        // ← Check
            // Imagine context switch HERE
            balance -= amount;           // ← Update
        }
    }
}

// Two threads try to withdraw $600 each
Thread 1: withdraw(600)
Thread 2: withdraw(600)
```

**What can happen:**

```
Thread 1: Check balance (1000) >= 600? ✓
Thread 2: Check balance (1000) >= 600? ✓  ← BOTH see 1000!
Thread 1: balance = 1000 - 600 = 400
Thread 2: balance = 1000 - 600 = 400      ← Wrong! Should be -200
```

**Result**: Balance is 400 instead of correct value. This is a **lost update**.

### Problem 2: Visibility Issues

```java
public class StopThread {
    private static boolean stopRequested = false;

    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(() -> {
            int i = 0;
            while (!stopRequested) {    // ← May never see update!
                i++;
            }
            System.out.println("Stopped after " + i);
        });
        thread.start();

        Thread.sleep(1000);
        stopRequested = true;           // ← Thread may not see this!
    }
}
```

**Problem**: Each thread has its own CPU cache. Thread may never see `stopRequested = true`.

### Problem 3: Atomicity

```java
count++; // Looks like one operation, but actually THREE:
// 1. Read current value
// 2. Add 1
// 3. Write new value

// If two threads do this simultaneously, increments can be lost
```

---

## Synchronization Mechanisms

### 1. synchronized Keyword

**synchronized** ensures only one thread can execute a block at a time.

#### Synchronized Method

```java
public class BankAccount {
    private int balance = 1000;

    public synchronized void withdraw(int amount) {
        if (balance >= amount) {
            balance -= amount;
        }
    }

    public synchronized int getBalance() {
        return balance;
    }
}
```

**How it works:**

- Every object in Java has an **intrinsic lock** (monitor)
- `synchronized` acquires the lock before entering method
- Releases lock when method exits
- Other threads WAIT for lock to be released

#### Synchronized Block

```java
public void withdraw(int amount) {
    synchronized(this) {  // Lock on 'this' object
        if (balance >= amount) {
            balance -= amount;
        }
    }
}

// Can lock on specific object
private final Object lock = new Object();

public void deposit(int amount) {
    synchronized(lock) {  // Lock on 'lock' object
        balance += amount;
    }
}
```

**Benefits of synchronized blocks:**

- More granular locking
- Can lock different objects for different operations
- Better performance (smaller critical section)

### 2. volatile Keyword

**volatile** ensures visibility across threads (but NOT atomicity).

```java
private volatile boolean stopRequested = false;

// Now all threads will see updates to stopRequested immediately
```

**When to use volatile:**

- Simple flags (boolean, reference)
- Single writer, multiple readers
- Don't need compound operations

**volatile vs synchronized:**

```java
// volatile: Good for flags
private volatile boolean ready = false;

// synchronized: Good for compound operations
private synchronized void increment() {
    count++; // Read-modify-write needs synchronization
}
```

### 3. Atomic Classes

Java provides atomic classes for lock-free thread-safe operations:

```java
import java.util.concurrent.atomic.*;

// AtomicInteger
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // Atomic increment
count.addAndGet(5);      // Atomic add

// Common atomic classes
AtomicInteger     // int
AtomicLong        // long
AtomicBoolean     // boolean
AtomicReference<T> // object reference
```

**Example: Thread-safe Counter**

```java
public class SafeCounter {
    private AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet(); // Thread-safe!
    }

    public int get() {
        return count.get();
    }
}
```

**Performance:**

- Atomic classes use CPU-level atomic instructions (CAS - Compare-And-Swap)
- Much faster than synchronized for simple operations
- No locks, no blocking

### 4. Locks (java.util.concurrent.locks)

More flexible than synchronized:

```java
import java.util.concurrent.locks.*;

public class BankAccount {
    private int balance = 1000;
    private final ReentrantLock lock = new ReentrantLock();

    public void withdraw(int amount) {
        lock.lock(); // Acquire lock
        try {
            if (balance >= amount) {
                balance -= amount;
            }
        } finally {
            lock.unlock(); // ALWAYS unlock in finally!
        }
    }
}
```

**Lock features synchronized doesn't have:**

- `tryLock()`: Try to acquire, don't block
- `tryLock(timeout)`: Wait up to timeout
- `lockInterruptibly()`: Can be interrupted while waiting
- ReadWriteLock: Multiple readers, single writer

**ReadWriteLock Example:**

```java
ReadWriteLock rwLock = new ReentrantReadWriteLock();
Lock readLock = rwLock.readLock();
Lock writeLock = rwLock.writeLock();

public int getBalance() {
    readLock.lock(); // Multiple threads can read simultaneously
    try {
        return balance;
    } finally {
        readLock.unlock();
    }
}

public void setBalance(int newBalance) {
    writeLock.lock(); // Only one thread can write
    try {
        balance = newBalance;
    } finally {
        writeLock.unlock();
    }
}
```

---

## Executor Framework

**Don't create threads manually!** Use the Executor framework.

### Why Executors?

Creating threads is expensive:

- Memory overhead (~1MB per thread)
- CPU overhead (context switching)
- Hard to manage lifecycle

**Solution**: Thread pools

### ExecutorService

```java
import java.util.concurrent.*;

// Create thread pool
ExecutorService executor = Executors.newFixedThreadPool(4); // 4 threads

// Submit tasks
for (int i = 0; i < 10; i++) {
    int taskId = i;
    executor.submit(() -> {
        System.out.println("Task " + taskId + " in " + Thread.currentThread().getName());
        // Do work
    });
}

// Shutdown when done
executor.shutdown(); // No new tasks accepted
executor.awaitTermination(1, TimeUnit.MINUTES); // Wait for completion
```

### Common Executor Types

```java
// Fixed thread pool: Fixed number of threads
ExecutorService executor = Executors.newFixedThreadPool(10);

// Cached thread pool: Creates threads as needed, reuses idle threads
ExecutorService executor = Executors.newCachedThreadPool();

// Single thread executor: One thread processes tasks sequentially
ExecutorService executor = Executors.newSingleThreadExecutor();

// Scheduled executor: Run tasks with delay or periodically
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
```

### Callable and Future

**Runnable** doesn't return a value. **Callable** does!

```java
// Callable: Can return result and throw exceptions
Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 42;
};

// Submit and get Future
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Integer> future = executor.submit(task);

// Future: Represents result of async computation
System.out.println("Task submitted, doing other work...");

// Get result (blocks until complete)
Integer result = future.get(); // Returns 42
System.out.println("Result: " + result);

// Can also check status
if (future.isDone()) {
    System.out.println("Task is complete");
}

// Can cancel
future.cancel(true);
```

### Example: Parallel Processing

```java
public class ParallelSum {
    public static void main(String[] args) throws Exception {
        int[] numbers = new int[1_000_000];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = i + 1;
        }

        // Split work into 4 chunks
        int numThreads = 4;
        int chunkSize = numbers.length / numThreads;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = (i == numThreads - 1) ? numbers.length : (i + 1) * chunkSize;

            Callable<Long> task = () -> {
                long sum = 0;
                for (int j = start; j < end; j++) {
                    sum += numbers[j];
                }
                return sum;
            };

            futures.add(executor.submit(task));
        }

        // Combine results
        long totalSum = 0;
        for (Future<Long> future : futures) {
            totalSum += future.get();
        }

        System.out.println("Total sum: " + totalSum);
        executor.shutdown();
    }
}
```

---

## Common Concurrency Patterns

### 1. Producer-Consumer

```java
BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);

// Producer
Thread producer = new Thread(() -> {
    for (int i = 0; i < 100; i++) {
        try {
            queue.put("Item " + i); // Blocks if queue is full
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
});

// Consumer
Thread consumer = new Thread(() -> {
    while (!Thread.interrupted()) {
        try {
            String item = queue.take(); // Blocks if queue is empty
            System.out.println("Consumed: " + item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
});

producer.start();
consumer.start();
```

### 2. Fork-Join Pattern

For divide-and-conquer algorithms:

```java
import java.util.concurrent.*;

public class SumTask extends RecursiveTask<Long> {
    private int[] array;
    private int start, end;
    private static final int THRESHOLD = 1000;

    @Override
    protected Long compute() {
        if (end - start <= THRESHOLD) {
            // Small enough: compute directly
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        } else {
            // Split into subtasks
            int mid = (start + end) / 2;
            SumTask left = new SumTask(array, start, mid);
            SumTask right = new SumTask(array, mid, end);

            left.fork(); // Execute asynchronously
            long rightResult = right.compute();
            long leftResult = left.join(); // Wait for result

            return leftResult + rightResult;
        }
    }
}

// Usage
ForkJoinPool pool = new ForkJoinPool();
long sum = pool.invoke(new SumTask(array, 0, array.length));
```

### 3. CountDownLatch

Coordinate multiple threads:

```java
CountDownLatch latch = new CountDownLatch(3); // 3 threads must finish

// Thread 1
new Thread(() -> {
    System.out.println("Thread 1 working...");
    latch.countDown(); // Decrement count
}).start();

// Thread 2
new Thread(() -> {
    System.out.println("Thread 2 working...");
    latch.countDown();
}).start();

// Thread 3
new Thread(() -> {
    System.out.println("Thread 3 working...");
    latch.countDown();
}).start();

// Main thread waits
latch.await(); // Blocks until count reaches 0
System.out.println("All threads finished!");
```

---

## Best Practices

### 1. Prefer Immutability

```java
// Immutable objects are automatically thread-safe
public final class ImmutablePerson {
    private final String name;
    private final int age;

    public ImmutablePerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // Only getters, no setters
}
```

### 2. Minimize Shared State

```java
// Bad: Shared counter
private int counter = 0;
executor.submit(() -> counter++); // NOT thread-safe

// Good: Independent tasks
executor.submit(() -> {
    int localCounter = 0; // Local variable, no sharing
    // Do work with localCounter
    return localCounter;
});
```

### 3. Use Concurrent Collections

```java
// Bad: Not thread-safe
List<String> list = new ArrayList<>();

// Good: Thread-safe
List<String> list = Collections.synchronizedList(new ArrayList<>());

// Better: Concurrent collection
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
BlockingQueue<String> queue = new LinkedBlockingQueue<>();
```

### 4. Always Use try-finally with Locks

```java
Lock lock = new ReentrantLock();

lock.lock();
try {
    // Critical section
} finally {
    lock.unlock(); // ALWAYS unlock
}
```

### 5. Avoid Holding Locks for Long Operations

```java
// Bad: Lock held during slow I/O
synchronized(this) {
    data = readFromDatabase(); // Slow!
}

// Good: Do slow work outside lock
Data data = readFromDatabase();
synchronized(this) {
    this.data = data; // Fast update
}
```

### 6. Be Careful with Thread Interruption

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Restore interrupt status
    // Clean up resources
    return;
}
```

---

## Summary

**Key Takeaways:**

✅ **Threads**: Lightweight processes for concurrent execution  
✅ **Race Conditions**: Multiple threads accessing shared data unsafely  
✅ **synchronized**: Ensures mutual exclusion  
✅ **volatile**: Ensures visibility (but not atomicity)  
✅ **Atomic Classes**: Lock-free thread-safe operations  
✅ **Executors**: Use thread pools instead of creating threads manually  
✅ **Future**: Get results from async computations  
✅ **Immutability**: Best way to achieve thread safety

**Next Steps:**

- Read [spring-concurrency.md](spring-concurrency.md) for Spring-specific patterns
- Read [locking-and-isolation.md](locking-and-isolation.md) for database concurrency
- Study our [ConcurrencyTest.java](/Users/emmanuel/Downloads/crashcourse/account-service/src/test/java/com/learn/accountservice/ConcurrencyTest.java) for real examples

Concurrency is powerful but complex. Start simple, test thoroughly! 🧵
