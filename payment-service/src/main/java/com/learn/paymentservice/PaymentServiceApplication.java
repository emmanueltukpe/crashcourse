package com.learn.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EDUCATIONAL NOTE - Payment Service with Event-Driven Architecture
 * 
 * This service handles payment processing and demonstrates:
 * 1. **Transactional Outbox Pattern** for reliable event publishing
 * 2. **Saga Pattern** for distributed transactions
 * 3. **Kafka** for asynchronous communication
 * 
 * @EnableScheduling allows us to run the OutboxPublisher periodically
 */
@SpringBootApplication
@EnableScheduling // Enables @Scheduled annotations for Outbox publisher
public class PaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentServiceApplication.class, args);
  }
}
