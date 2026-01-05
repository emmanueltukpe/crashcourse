package com.learn.common.dto;

import com.learn.common.enums.Currency;
import com.learn.common.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for initiating a payment
 */
public class PaymentRequest {

  @NotNull(message = "User ID is required")
  private Long userId;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  @NotNull(message = "Currency is required")
  private Currency currency;

  @NotNull(message = "Payment type is required")
  private PaymentType paymentType;

  private String description;

  // For TRANSFER type: recipient user ID
  private Long recipientUserId;

  // Default constructor for Jackson
  public PaymentRequest() {
  }

  public PaymentRequest(Long userId, BigDecimal amount, Currency currency, PaymentType paymentType) {
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.paymentType = paymentType;
  }

  // Getters and Setters

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(Long recipientUserId) {
    this.recipientUserId = recipientUserId;
  }
}
