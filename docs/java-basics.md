# Java Basics - A Beginner's Guide

Welcome to Java! This guide covers the fundamental concepts you need to understand the cross-border payment simulator project.

## Table of Contents

1. [What is Java?](#what-is-java)
2. [Variables and Data Types](#variables-and-data-types)
3. [Classes and Objects](#classes-and-objects)
4. [Methods](#methods)
5. [Packages and Imports](#packages-and-imports)
6. [Collections](#collections)
7. [Exception Handling](#exception-handling)
8. [Maven Basics](#maven-basics)

---

## What is Java?

Java is a **programming language** and **platform** created by Sun Microsystems (now owned by Oracle) in 1995.

**Key characteristics:**

- **Object-Oriented**: Everything in Java is an object (except primitives)
- **Platform-Independent**: "Write once, run anywhere" - Java code runs on any device with a JVM (Java Virtual Machine)
- **Statically Typed**: Variable types are declared and checked at compile time
- **Garbage Collected**: Automatic memory management - no need to manually free memory

**How Java works:**

```
Source Code (.java) → Compiler → Bytecode (.class) → JVM → Machine Code
```

---

## Variables and Data Types

### Primitives (Basic Types)

Java has 8 primitive types - they're not objects, just simple values:

```java
// Integers (whole numbers)
byte age = 25;           // -128 to 127
short year = 2025;       // -32,768 to 32,767
int count = 1000000;     // -2 billion to 2 billion
long bigNumber = 9999999999L;  // Very large numbers (note the 'L')

// Decimals
float price = 19.99f;    // 32-bit decimal (note the 'f')
double precise = 19.99;  // 64-bit decimal (more precise, prefer this)

// Characters and Booleans
char letter = 'A';       // Single character
boolean isActive = true; // true or false
```

### Reference Types (Objects)

Everything else is an object:

```java
String name = "Emmanuel";         // Text
BigDecimal money = new BigDecimal("100.50");  // Precise decimal for money
LocalDateTime now = LocalDateTime.now();      // Date and time
```

**Why use `BigDecimal` for money?**

```java
// DON'T DO THIS - double has precision errors!
double balance = 0.1 + 0.2;  // Result: 0.30000000000000004 😱

// DO THIS - BigDecimal is precise
BigDecimal balance = new BigDecimal("0.1").add(new BigDecimal("0.2"));
// Result: exactly 0.3 ✅
```

### Variable Declaration

```java
// Type variableName = value;
String email = "user@example.com";

// You can declare without initializing
int count;
count = 10;  // Assign later

// Final variables (constants) can't be changed
final double PI = 3.14159;
// PI = 3.14; // ERROR! Can't reassign
```

---

## Classes and Objects

### What is a Class?

A **class** is a blueprint for creating objects. Think of it like a cookie cutter - the class is the cutter, objects are the cookies.

```java
// Define a class
public class User {
    // Fields (data that each User object will have)
    private String email;
    private String passwordHash;

    // Constructor (how to create a User object)
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getter method (how to read the email)
    public String getEmail() {
        return email;
    }

    // Setter method (how to change the email)
    public void setEmail(String email) {
        this.email = email;
    }
}
```

### Creating and Using Objects

```java
// Create a new User object
User user1 = new User("alice@example.com", "hashedPassword123");
User user2 = new User("bob@example.com", "hashedPassword456");

// Use the methods
String email = user1.getEmail();  // "alice@example.com"
user2.setEmail("bob.new@example.com");  // Change email
```

### Access Modifiers

- `public` - Anyone can access
- `private` - Only this class can access
- `protected` - This class and subclasses can access
- (no modifier) - Package-private, only this package can access

**Best practice**: Make fields `private` and provide `public` getters/setters. This encapsulation gives you control over how data is accessed.

### Inheritance

Classes can inherit from other classes:

```java
// Parent class
public class Animal {
    public void makeSound() {
        System.out.println("Some sound");
    }
}

// Child class (extends Animal)
public class Dog extends Animal {
    @Override  // Override the parent method
    public void makeSound() {
        System.out.println("Woof!");
    }
}

// Usage
Dog dog = new Dog();
dog.makeSound();  // Prints "Woof!"
```

---

## Methods

Methods are functions that belong to a class. They define what objects can do.

### Method Structure

```java
// accessModifier returnType methodName(parameters) {
//     // method body
// }

public String greet(String name) {
    return "Hello, " + name;
}
```

### Method Types

**Instance methods** - operate on specific objects:

```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}

Calculator calc = new Calculator();
int result = calc.add(5, 3);  // 8
```

**Static methods** - belong to the class, not objects:

```java
public class MathUtils {
    public static int max(int a, int b) {
        return (a > b) ? a : b;
    }
}

int result = MathUtils.max(10, 20);  // No object needed!
```

**Void methods** - don't return anything:

```java
public void printMessage(String message) {
    System.out.println(message);
    // No return statement
}
```

---

## Packages and Imports

### What are Packages?

Packages organize classes into namespaces, like folders for code:

```java
package com.learn.authservice.entity;

// This User class is in the com.learn.authservice.entity package
public class User {
    // ...
}
```

### Imports

To use classes from other packages, import them:

```java
// Import a specific class
import java.time.LocalDateTime;

// Import all classes from a package
import java.util.*;

// Use the imported class
LocalDateTime now = LocalDateTime.now();
```

**No import needed for:**

- Classes in the same package
- Classes in `java.lang` (like String, System, Math)

---

## Collections

Collections store multiple items.

### List (Ordered Collection)

```java
import java.util.ArrayList;
import java.util.List;

// Create a list
List<String> names = new ArrayList<>();

// Add items
names.add("Alice");
names.add("Bob");
names.add("Charlie");

// Access by index
String first = names.get(0);  // "Alice"

// Loop through list
for (String name : names) {
    System.out.println(name);
}

// Size
int count = names.size();  // 3
```

### Map (Key-Value Pairs)

```java
import java.util.HashMap;
import java.util.Map;

// Create a map
Map<String, Integer> ages = new HashMap<>();

// Add key-value pairs
ages.put("Alice", 25);
ages.put("Bob", 30);

// Get value by key
int aliceAge = ages.get("Alice");  // 25

// Check if key exists
if (ages.containsKey("Charlie")) {
    // ...
}
```

### Set (Unique Items)

```java
import java.util.HashSet;
import java.util.Set;

Set<String> uniqueEmails = new HashSet<>();
uniqueEmails.add("alice@example.com");
uniqueEmails.add("bob@example.com");
uniqueEmails.add("alice@example.com");  // Duplicate - won't be added

System.out.println(uniqueEmails.size());  // 2 (duplicates removed)
```

---

## Exception Handling

Exceptions are errors that occur during program execution.

### Try-Catch

```java
try {
    // Code that might throw an exception
    int result = 10 / 0;  // Division by zero!
} catch (ArithmeticException e) {
    // Handle the error
    System.out.println("Error: " + e.getMessage());
} finally {
    // This always executes (optional)
    System.out.println("Done");
}
```

### Throwing Exceptions

```java
public void checkAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("Age cannot be negative");
    }
}
```

### Custom Exceptions

```java
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

// Usage
if (balance < amount) {
    throw new InsufficientFundsException("Not enough money");
}
```

---

## Maven Basics

Maven is a **build tool** that manages dependencies and builds Java projects.

### Project Structure

```
project/
├── pom.xml           ← Maven configuration
└── src/
    ├── main/
    │   ├── java/     ← Your code
    │   └── resources/ ← Config files
    └── test/
        └── java/     ← Test code
```

### pom.xml (Project Object Model)

```xml
<project>
    <groupId>com.learn</groupId>
    <artifactId>my-project</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <!-- Add external libraries here -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Maven Commands

```bash
# Clean compiled files
./mvnw clean

# Compile code
./mvnw compile

# Run tests
./mvnw test

# Package as JAR file
./mvnw package

# Clean and package
./mvnw clean package
```

---

## Quick Reference Card

```java
// Variable
String name = "Emmanuel";

// Class
public class User { }

// Method
public String getName() { return name; }

// Object creation
User user = new User();

// Conditional
if (age > 18) { } else { }

// Loop
for (int i = 0; i < 10; i++) { }
for (String item : list) { }

// Exception
try { } catch (Exception e) { } finally { }
```

---

## Next Steps

Now that you understand Java basics, proceed to [Spring Boot Basics](./spring-boot-basics.md) to learn how Spring Boot simplifies Java development!
