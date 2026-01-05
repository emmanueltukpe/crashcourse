package com.learn.common.dto;

import com.learn.common.enums.Currency;
import com.learn.common.enums.PaymentEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - Kafka Event DTO
 * 
 * This DTO represents an event that gets published to Kafka.
 * Events are immutable facts about things that happened in the system.
 * 
 * Key Properties of Events:
 * - Immutable: Once created, they don't change
 * - Past tense naming: "PaymentInitiated" not "InitiatePayment"
 * - Self-contained: Includes all data needed by consumers
 * - Timestamped: Always know when event occurred
 * 
 * Event-Driven Design:
 * - Publisher doesn't know who consumes the event
 * - Consumers can be added/removed without changing publisher
 * - Events can be replayed for debugging or recovery
 * - Events form an audit trail
 */
public class PaymentEvent {

  /**
   * Unique payment ID
   */
  private Long paymentId;

  /**
   * User who initiated payment
   */
  private Long userId;

  /**
   * Payment amount
   */
  private BigDecimal amount;

  /**
   * Payment currency
   */
  private Currency currency;

  /**
   * Type of event (INITIATED, COMPLETED, etc.)
   */
  private PaymentEventType eventType;

  /**
   * When the event occurred
   */
  private LocalDateTime timestamp;

  /**
   * Optional message or description
   */
  private String message;

  // Default constructor for Jackson deserialization
  public PaymentEvent() {
  }

  public PaymentEvent(Long paymentId, Long userId, BigDecimal amount,
      Currency currency, PaymentEventType eventType) {
    this.paymentId = paymentId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.eventType = eventType;
    this.timestamp = LocalDateTime.now();
  }

  // Getters and Setters

  public Long getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(Long paymentId) {
    this.paymentId = paymentId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public Currency getCurrency() {
    return currency;
  }

  public void setCurrency(Currency currency) {
    this.currency = currency;
  }

  public PaymentEventType getEventType() {
    return eventType;
  }

  public void setEventType(PaymentEventType eventType) {
    this.eventType = eventType;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "PaymentEvent{" +
        "paymentId=" + paymentId +
        ", userId=" + userId +
        ", amount=" + amount +
        ", currency=" + currency +
        ", eventType=" + eventType +
        ", timestamp=" + timestamp +
        '}';
  }
}
