package com.learn.paymentservice.entity;

import com.learn.common.enums.Currency;
import com.learn.common.enums.PaymentStatus;
import com.learn.common.enums.PaymentType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - Payment Entity
 * 
 * This entity represents a payment in the system.
 * It demonstrates several important JPA and business logic concepts.
 */
@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * User who initiated the payment
   */
  @Column(nullable = false)
  private Long userId;

  /**
   * Payment amount
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  /**
   * Payment currency
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Currency currency;

  /**
   * Type of payment
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentType paymentType;

  /**
   * Current status of payment
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  /**
   * Optional description
   */
  @Column(length = 500)
  private String description;

  /**
   * For TRANSFER payments: recipient user
   */
  @Column
  private Long recipientUserId;

  /**
   * When payment was created
   */
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * When payment was last updated
   */
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Default constructor for JPA
  public Payment() {
  }

  public Payment(Long userId, BigDecimal amount, Currency currency, PaymentType paymentType) {
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.paymentType = paymentType;
    this.status = PaymentStatus.PENDING;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (status == null) {
      status = PaymentStatus.PENDING;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public Long getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(Long recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
