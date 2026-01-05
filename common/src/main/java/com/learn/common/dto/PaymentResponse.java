package com.learn.common.dto;

import com.learn.common.enums.Currency;
import com.learn.common.enums.PaymentStatus;
import com.learn.common.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for payment operations
 */
public class PaymentResponse {

  private Long paymentId;
  private Long userId;
  private BigDecimal amount;
  private Currency currency;
  private PaymentType paymentType;
  private PaymentStatus status;
  private String description;
  private LocalDateTime createdAt;
  private String message;

  // Default constructor
  public PaymentResponse() {
  }

  public PaymentResponse(Long paymentId, PaymentStatus status, String message) {
    this.paymentId = paymentId;
    this.status = status;
    this.message = message;
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

  public PaymentType getPaymentType() {
    return paymentType;
  }

  public void setPaymentType(PaymentType paymentType) {
    this.paymentType = paymentType;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
