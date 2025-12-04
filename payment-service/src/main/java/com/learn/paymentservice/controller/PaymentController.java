package com.learn.paymentservice.controller;

import com.learn.common.dto.PaymentRequest;
import com.learn.common.dto.PaymentResponse;
import com.learn.paymentservice.entity.Payment;
import com.learn.paymentservice.service.OutboxPublisher;
import com.learn.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for payment operations
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

  private final PaymentService paymentService;
  private final OutboxPublisher outboxPublisher;

  public PaymentController(PaymentService paymentService, OutboxPublisher outboxPublisher) {
    this.paymentService = paymentService;
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Initiate a new payment
   * 
   * POST /api/v1/payments
   * Body: {"userId": 1, "amount": 100.00, "currency": "USD", "paymentType":
   * "CONVERSION"}
   */
  @PostMapping
  public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
    PaymentResponse response = paymentService.initiatePayment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Get payment by ID
   * 
   * GET /api/v1/payments/{id}
   */
  @GetMapping("/{id}")
  public ResponseEntity<Payment> getPayment(@PathVariable Long id) {
    Payment payment = paymentService.getPaymentById(id);
    return ResponseEntity.ok(payment);
  }

  /**
   * Get count of pending outbox events (for monitoring)
   * 
   * GET /api/v1/payments/outbox/pending
   */
  @GetMapping("/outbox/pending")
  public ResponseEntity<Long> getPendingOutboxCount() {
    long count = outboxPublisher.getPendingEventCount();
    return ResponseEntity.ok(count);
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Payment service is running");
  }
}
