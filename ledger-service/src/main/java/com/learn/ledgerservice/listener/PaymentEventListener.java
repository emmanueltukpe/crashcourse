package com.learn.ledgerservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.common.dto.PaymentEvent;
import com.learn.ledgerservice.entity.LedgerEntry;
import com.learn.ledgerservice.repository.LedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDUCATIONAL NOTE - Kafka Consumer with @KafkaListener
 * 
 * This is the other half of our event-driven architecture!
 * While payment-service PRODUCES events, this service CONSUMES them.
 * 
 * ═══════════════════════════════════════════════════════════════
 * HOW KAFKA CONSUMERS WORK
 * ═══════════════════════════════════════════════════════════════
 * 
 * 1. **Consumer Groups**: Consumers belong to a group (configured below)
 * 2. **Partition Assignment**: Each partition is consumed by one consumer in
 * the group
 * 3. **Offset Tracking**: Kafka remembers which messages have been processed
 * 4. **At-Least-Once Delivery**: Messages may be delivered more than once
 * 5. **Ordering**: Messages within a partition are ordered
 * 
 * Example with 3 partitions and 2 consumers in same group:
 * Partition 0 → Consumer 1
 * Partition 1 → Consumer 2
 * Partition 2 → Consumer 1
 * 
 * If Consumer 1 dies:
 * Partition 0 → Consumer 2 (rebalance)
 * Partition 1 → Consumer 2
 * Partition 2 → Consumer 2
 * 
 * ═══════════════════════════════════════════════════════════════
 * 
 * @KafkaListener ANNOTATION
 *                ═══════════════════════════════════════════════════════════════
 * 
 * @KafkaListener(topics = "payment-events", groupId = "ledger-service")
 * 
 *                       - topics: Which Kafka topics to listen to
 *                       - groupId: Consumer group ID (multiple instances share
 *                       work)
 * 
 *                       Spring automatically:
 *                       - Connects to Kafka
 *                       - Subscribes to topics
 *                       - Deserializes messages
 *                       - Calls this method for each message
 *                       - Commits offsets after successful processing
 * 
 *                       ═══════════════════════════════════════════════════════════════
 *                       IDEMPOTENCY - THE CRITICAL CONCEPT
 *                       ═══════════════════════════════════════════════════════════════
 * 
 *                       Kafka delivers "at-least-once" which means:
 *                       - Same message may be delivered multiple times
 *                       - Consumer crashes after processing but before
 *                       committing offset
 *                       - Network issues cause retries
 * 
 *                       We MUST handle duplicates! Methods:
 * 
 *                       1. **Unique Constraint** (what we use):
 *                       - Store message ID in database
 *                       - Unique constraint prevents duplicates
 *                       - If duplicate, INSERT fails gracefully
 * 
 *                       2. **Check Before Processing**:
 *                       - Query database for message ID
 *                       - If exists, skip processing
 *                       - If not, process and save
 * 
 *                       3. **Idempotent Operations**:
 *                       - Design operations that are safe to repeat
 *                       - Example: SET balance = 100 (idempotent)
 *                       - Example: INCREMENT balance (NOT idempotent!)
 * 
 *                       ═══════════════════════════════════════════════════════════════
 *                       SAGA PATTERN - CHOREOGRAPHY
 *                       ═══════════════════════════════════════════════════════════════
 * 
 *                       This listener participates in a Saga:
 * 
 *                       1. Payment Service: Creates payment → Publishes
 *                       PAYMENT_INITIATED
 *                       2. Ledger Service (here): Consumes event → Creates
 *                       ledger entry
 *                       3. If successful: Could publish LEDGER_RECORDED for
 *                       other services
 *                       4. If failure: Could publish LEDGER_FAILED → Payment
 *                       service compensates
 * 
 *                       This is "choreography" because:
 *                       - Each service knows what to do when it sees an event
 *                       - No central coordinator
 *                       - Services react to events independently
 * 
 *                       Alternative: "Orchestration"
 *                       - Central orchestrator tells each service what to do
 *                       - More control, but creates coupling
 *                       - We'll explore in Saga documentation
 */
@Component
public class PaymentEventListener {

  private static final Logger logger = LoggerFactory.getLogger(PaymentEventListener.class);

  private final LedgerRepository ledgerRepository;
  private final ObjectMapper objectMapper;

  public PaymentEventListener(LedgerRepository ledgerRepository, ObjectMapper objectMapper) {
    this.ledgerRepository = ledgerRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Listen for payment events from Kafka
   * 
   * @Payload: The message body (JSON string)
   * @Header: Extract header values (like message ID for idempotency)
   */
  @KafkaListener(topics = "payment-events", groupId = "ledger-service")
  @Transactional
  public void handlePaymentEvent(
      @Payload String eventJson,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      @Header(KafkaHeaders.OFFSET) Long offset,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

    logger.info("Received payment event: partition={}, offset={}, key={}",
        partition, offset, key);

    try {
      // Deserialize JSON to PaymentEvent object
      PaymentEvent event = objectMapper.readValue(eventJson, PaymentEvent.class);

      logger.info("Processing payment event: paymentId={}, eventType={}, amount={}",
          event.getPaymentId(), event.getEventType(), event.getAmount());

      // Create unique message ID for idempotency
      // Format: partition-offset (guaranteed unique within topic)
      String messageId = String.format("payment-events-%d-%d", partition, offset);

      // Check if we've already processed this message (idempotency check)
      if (ledgerRepository.existsByMessageId(messageId)) {
        logger.warn("Duplicate message detected (messageId={}), skipping processing", messageId);
        return; // Skip - already processed
      }

      // Create ledger entry
      LedgerEntry entry = new LedgerEntry(
          event.getPaymentId(),
          event.getUserId(),
          event.getAmount(),
          event.getCurrency(),
          event.getEventType());
      entry.setDescription(event.getMessage());
      entry.setMessageId(messageId); // For idempotency

      // Save to database (append-only ledger)
      ledgerRepository.save(entry);

      logger.info("Ledger entry created: id={}, paymentId={}, amount={} {}",
          entry.getId(), entry.getPaymentId(),
          entry.getAmount(), entry.getCurrency());

      // In a real Saga, we might publish LEDGER_RECORDED event here
      // For Stage 2, we keep it simple

    } catch (JsonProcessingException e) {
      logger.error("Failed to parse payment event: {}", e.getMessage(), e);
      // Exception will cause Kafka to NOT commit offset
      // Message will be redelivered
      throw new RuntimeException("Failed to process payment event", e);

    } catch (Exception e) {
      logger.error("Error processing payment event: {}", e.getMessage(), e);
      // Depending on error, you might want to:
      // - Retry (throw exception)
      // - Skip (log and return)
      // - Send to dead letter queue
      throw new RuntimeException("Failed to process payment event", e);
    }
  }
}
