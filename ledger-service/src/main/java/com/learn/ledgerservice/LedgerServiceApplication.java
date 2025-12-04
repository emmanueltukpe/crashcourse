package com.learn.ledgerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EDUCATIONAL NOTE - Ledger Service
 * 
 * This service demonstrates:
 * 1. **Kafka Consumer** - Listening to events from payment-service
 * 2. **Event-Driven Architecture** - Reacting to events asynchronously
 * 3. **Saga Pattern** - Participating in distributed transactions
 * 4. **Idempotency** - Handling duplicate events safely
 */
@SpringBootApplication
public class LedgerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(LedgerServiceApplication.class, args);
  }
}
