package com.learn.paymentservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - Outbox Event Entity
 * 
 * This is the HEART of the Transactional Outbox Pattern!
 * 
 * ═══════════════════════════════════════════════════════════════
 * THE PROBLEM: Dual-Write Problem
 * ═══════════════════════════════════════════════════════════════
 * 
 * Imagine this scenario:
 * 
 * @Transactional
 *                public void processPayment(Payment payment) {
 *                paymentRepository.save(payment); // Write to DB
 *                kafkaTemplate.send("topic", paymentEvent); // Send to Kafka
 *                }
 * 
 *                What happens if:
 *                1. Database save succeeds, but Kafka is down? → Payment saved
 *                but no event! ❌
 *                2. Kafka send succeeds, but DB transaction rolls back? → Event
 *                sent but no payment! ❌
 * 
 *                This is called the "dual-write problem" - you can't make two
 *                separate systems
 *                atomic in a single transaction.
 * 
 *                ═══════════════════════════════════════════════════════════════
 *                THE SOLUTION: Transactional Outbox Pattern
 *                ═══════════════════════════════════════════════════════════════
 * 
 *                Instead of writing to Kafka directly, we:
 * 
 *                1. Save payment to database (payments table)
 *                2. Save event to outbox table IN THE SAME DATABASE TRANSACTION
 *                3. Both commit together (ACID guarantees!)
 *                4. A separate OutboxPublisher polls the outbox table
 *                5. Publishes unpublished events to Kafka
 *                6. Marks them as published
 * 
 *                Flow:
 * @Transactional
 *                public void processPayment() {
 *                paymentRepo.save(payment); // 1. Save payment
 *                outboxRepo.save(outboxEvent); // 2. Save event (same TX!)
 *                // Both commit atomically ✅
 *                }
 * 
 * @Scheduled
 *            public void publishEvents() {
 *            List<OutboxEvent> pending = outboxRepo.findUnpublished();
 *            for (OutboxEvent event : pending) {
 *            kafkaTemplate.send(event.getPayload()); // 3. Publish to Kafka
 *            event.setPublished(true); // 4. Mark published
 *            outboxRepo.save(event);
 *            }
 *            }
 * 
 *            KEY BENEFITS:
 *            ✅ Atomicity: Payment + Event save together (single DB transaction)
 *            ✅ Reliability: If Kafka is down, events accumulate in outbox
 *            ✅ Guaranteed delivery: Events will eventually be published
 *            ✅ No data loss: Database is single source of truth
 * 
 *            TRADE-OFFS:
 *            - Eventual consistency (slight delay between save and event
 *            publish)
 *            - Extra database table required
 *            - Polling overhead (can be optimized with CDC like Debezium)
 * 
 *            ALTERNATIVES:
 *            - Change Data Capture (CDC): Monitor DB transaction log directly
 *            - Two-Phase Commit (2PC): Avoid in distributed systems (slow,
 *            blocking)
 *            - Saga Pattern: For multi-service transactions
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Type of aggregate this event relates to
   * Example: "Payment", "Order", "User"
   */
  @Column(nullable = false, length = 100)
  private String aggregateType;

  /**
   * ID of the aggregate
   * Example: paymentId, orderId
   */
  @Column(nullable = false)
  private Long aggregateId;

  /**
   * Type of event
   * Example: "PAYMENT_INITIATED", "ORDER_PLACED"
   */
  @Column(nullable = false, length = 100)
  private String eventType;

  /**
   * Event payload as JSON string
   * Contains all data needed by consumers
   */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  /**
   * Whether this event has been published to Kafka
   * false = pending, true = published
   */
  @Column(nullable = false)
  private Boolean published = false;

  /**
   * When the event occurred (business time)
   */
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * When the event was published to Kafka (technical time)
   */
  @Column
  private LocalDateTime publishedAt;

  // Default constructor for JPA
  public OutboxEvent() {
  }

  public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.published = false;
    this.createdAt = LocalDateTime.now();
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (published == null) {
      published = false;
    }
  }

  /**
   * Mark this event as published
   */
  public void markAsPublished() {
    this.published = true;
    this.publishedAt = LocalDateTime.now();
  }

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public void setAggregateType(String aggregateType) {
    this.aggregateType = aggregateType;
  }

  public Long getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(Long aggregateId) {
    this.aggregateId = aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Boolean getPublished() {
    return published;
  }

  public void setPublished(Boolean published) {
    this.published = published;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(LocalDateTime publishedAt) {
    this.publishedAt = publishedAt;
  }
}
