package com.learn.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.common.dto.PaymentEvent;
import com.learn.common.dto.PaymentRequest;
import com.learn.common.dto.PaymentResponse;
import com.learn.common.enums.PaymentEventType;
import com.learn.common.enums.PaymentStatus;
import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.entity.Payment;
import com.learn.paymentservice.repository.OutboxRepository;
import com.learn.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDUCATIONAL NOTE - Payment Service with Transactional Outbox
 * 
 * This service demonstrates the Transactional Outbox pattern in practice.
 * Pay close attention to how we save BOTH the payment AND the outbox event
 * in a SINGLE database transaction.
 */
@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public PaymentService(PaymentRepository paymentRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.paymentRepository = paymentRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * EDUCATIONAL NOTE - Transactional Outbox in Action
   * 
   * This method shows the Transactional Outbox pattern:
   * 
   * ═══════════════════════════════════════════════════════════════
   * THE KEY INSIGHT
   * ═══════════════════════════════════════════════════════════════
   * 
   * We DON'T publish to Kafka here! Instead, we:
   * 1. Save the payment to the payments table
   * 2. Save an event to the outbox_events table
   * 3. Both happen in the SAME database transaction
   * 4. A separate OutboxPublisher will publish to Kafka later
   * 
   * Why is this better?
   * ✅ Atomicity: Payment + Event are saved together or not at all
   * ✅ Reliability: If Kafka is down, events are safely stored
   * ✅ Guaranteed delivery: Events will eventually reach Kafka
   * ✅ No data loss: Database is single source of truth
   * 
   * The flow:
   * 
   * @Transactional starts → database transaction begins
   *                ├─ Save payment
   *                ├─ Create outbox event
   *                ├─ Save outbox event
   *                └─ @Transactional ends → COMMIT both saves together
   * 
   *                Later (separate process):
   *                OutboxPublisher finds unpublished events → publishes to Kafka
   * 
   *                ═══════════════════════════════════════════════════════════════
   */
  @Transactional
  public PaymentResponse initiatePayment(PaymentRequest request) {
    // Step 1: Create and save payment entity
    Payment payment = new Payment(
        request.getUserId(),
        request.getAmount(),
        request.getCurrency(),
        request.getPaymentType());
    payment.setDescription(request.getDescription());
    payment.setRecipientUserId(request.getRecipientUserId());
    payment.setStatus(PaymentStatus.PENDING);

    // Save to database (part of transaction)
    payment = paymentRepository.save(payment);

    // Step 2: Create event for Kafka (but don't publish yet!)
    PaymentEvent paymentEvent = new PaymentEvent(
        payment.getId(),
        payment.getUserId(),
        payment.getAmount(),
        payment.getCurrency(),
        PaymentEventType.PAYMENT_INITIATED);
    paymentEvent.setMessage("Payment initiated");

    // Step 3: Save event to outbox table (same transaction!)
    try {
      String eventPayload = objectMapper.writeValueAsString(paymentEvent);

      OutboxEvent outboxEvent = new OutboxEvent(
          "Payment", // aggregateType
          payment.getId(), // aggregateId
          "PAYMENT_INITIATED", // eventType
          eventPayload // payload (JSON)
      );

      outboxRepository.save(outboxEvent);

      // Transaction will commit here!
      // Both payment AND outbox event are now in the database

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize payment event", e);
    }

    // Step 4: Return response
    PaymentResponse response = new PaymentResponse(
        payment.getId(),
        payment.getStatus(),
        "Payment initiated successfully");
    response.setUserId(payment.getUserId());
    response.setAmount(payment.getAmount());
    response.setCurrency(payment.getCurrency());
    response.setPaymentType(payment.getPaymentType());
    response.setCreatedAt(payment.getCreatedAt());

    return response;
  }

  /**
   * Get payment by ID
   */
  @Transactional(readOnly = true)
  public Payment getPaymentById(Long paymentId) {
    if (paymentId == null) {
      throw new RuntimeException("Payment ID cannot be null");
    }
    return paymentRepository.findById(paymentId)
        .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
  }

  /**
   * Update payment status
   * Called when ledger service confirms the payment
   */
  @Transactional
  public void updatePaymentStatus(Long paymentId, PaymentStatus newStatus) {
    if (paymentId == null) {
      throw new RuntimeException("Payment ID cannot be null");
    }
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

    payment.setStatus(newStatus);
    paymentRepository.save(payment);
  }
}
