package com.learn.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.common.dto.PaymentEvent;
import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EDUCATIONAL NOTE - Outbox Publisher
 * 
 * This is the second half of the Transactional Outbox pattern!
 * 
 * ═══════════════════════════════════════════════════════════════
 * HOW IT WORKS
 * ═══════════════════════════════════════════════════════════════
 * 
 * This service runs periodically (@Scheduled) and:
 * 1. Queries the outbox table for unpublished events
 * 2. Publishes each event to Kafka
 * 3. Marks the event as published
 * 
 * Schedule:
 * We run every 5 seconds. In production, tune this based on:
 * - How quickly events must be delivered
 * - Database load (more frequent = more queries)
 * - Event volume (lots of events = run more often)
 * 
 * Error Handling:
 * - If Kafka publish fails, we DON'T mark as published
 * - Event remains in outbox
 * - Next run will retry
 * - Eventually consistent delivery!
 * 
 * ═══════════════════════════════════════════════════════════════
 * WHY NOT @Scheduled in PaymentService?
 * ═══════════════════════════════════════════════════════════════
 * 
 * Separation of concerns:
 * - PaymentService: Business logic + Transactional writes
 * - OutboxPublisher: Infrastructure concern (Kafka integration)
 * 
 * This also allows us to:
 * - Scale publishers independently
 * - Use different strategies (polling vs CDC)
 * - Monitor/alert on publishing lag
 * 
 * ═══════════════════════════════════════════════════════════════
 * ALTERNATIVE: Change Data Capture (CDC)
 * ═══════════════════════════════════════════════════════════════
 * 
 * Instead of polling, tools like Debezium can:
 * - Monitor PostgreSQL transaction log
 * - Detect new outbox rows immediately
 * - Publish to Kafka with near-zero latency
 * - No polling overhead!
 * 
 * We use polling for Stage 2 because it's simpler to understand.
 * Stage 4-5 could explore CDC with Debezium.
 */
@Service
public class OutboxPublisher {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository outboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  // Kafka topic name
  private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

  public OutboxPublisher(OutboxRepository outboxRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Publish pending outbox events to Kafka
   * 
   * @Scheduled runs this method periodically
   *            - fixedDelay = 5000: Wait 5 seconds after previous execution
   *            completes
   *            - initialDelay = 10000: Wait 10 seconds after startup (let app
   *            initialize)
   * 
   *            If this method takes 2 seconds to run, schedule is:
   *            Start → Wait 10s → Run (2s) → Wait 5s → Run (2s) → Wait 5s → ...
   */
  @Scheduled(fixedDelay = 5000, initialDelay = 10000)
  @Transactional
  public void publishPendingEvents() {
    // Find all unpublished events (ordered by creation time)
    List<OutboxEvent> pendingEvents = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();

    if (pendingEvents.isEmpty()) {
      // No events to publish - this is normal!
      return;
    }

    logger.info("Found {} unpublished events to publish", pendingEvents.size());

    int successCount = 0;
    int failCount = 0;

    for (OutboxEvent event : pendingEvents) {
      try {
        // Publish to Kafka
        publishEvent(event);

        // Mark as published
        event.markAsPublished();
        outboxRepository.save(event);

        successCount++;

        logger.debug("Published event: {} (ID: {})", event.getEventType(), event.getId());

      } catch (Exception e) {
        // Log error but continue with other events
        logger.error("Failed to publish event ID {}: {}",
            event.getId(), e.getMessage(), e);
        failCount++;

        // Event remains unpublished - will retry next time
      }
    }

    logger.info("Outbox publisher completed: {} success, {} failed", successCount, failCount);
  }

  /**
   * Publish a single event to Kafka
   */
  private void publishEvent(OutboxEvent outboxEvent) {
    try {
      // The payload is already JSON string
      String payload = outboxEvent.getPayload();

      // Parse to ensure it's valid JSON
      PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);

      // Publish to Kafka
      // Key = payment ID (for partitioning - same payment always goes to same
      // partition)
      // Value = event JSON
      kafkaTemplate.send(
          PAYMENT_EVENTS_TOPIC,
          event.getPaymentId().toString(), // Key
          payload // Value
      );

      logger.debug("Published to Kafka: topic={}, key={}, eventType={}",
          PAYMENT_EVENTS_TOPIC, event.getPaymentId(), event.getEventType());

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Invalid JSON in outbox event: " + outboxEvent.getId(), e);
    }
  }

  /**
   * Get count of pending events (for monitoring/health)
   */
  public long getPendingEventCount() {
    return outboxRepository.countByPublishedFalse();
  }
}
