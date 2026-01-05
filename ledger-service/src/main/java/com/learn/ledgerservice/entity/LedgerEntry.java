package com.learn.ledgerservice.entity;

import com.learn.common.enums.Currency;
import com.learn.common.enums.PaymentEventType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - Ledger Entry Entity
 * 
 * A ledger is an immutable, append-only log of all financial transactions.
 * Think of it like an accounting book where you never erase, only add.
 * 
 * Key Properties:
 * - **Immutable**: Once created, never modified (no setters after save!)
 * - **Append-only**: Only INSERT, never UPDATE or DELETE
 * - **Audit trail**: Complete history of all transactions
 * - **Reconciliation**: Can rebuild account balances from ledger
 * 
 * In Production:
 * - Ledger is often the authoritative record
 * - Account balances are derived/cached state
 * - Can detect discrepancies by comparing
 * - Essential for auditing and compliance
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Payment ID this entry relates to
   * Used for linking back to payment
   */
  @Column(nullable = false)
  private Long paymentId;

  /**
   * User who owns this transaction
   */
  @Column(nullable = false)
  private Long userId;

  /**
   * Transaction amount
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  /**
   * Currency of the transaction
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Currency currency;

  /**
   * Type of ledger entry
   * Maps to the payment event type
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentEventType entryType;

  /**
   * Description of transaction
   */
  @Column(length = 500)
  private String description;

  /**
   * When this entry was created
   * Immutable - never changes
   */
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * Kafka message ID for idempotency
   * Prevents processing same event twice
   */
  @Column(unique = true)
  private String messageId;

  // Default constructor for JPA
  public LedgerEntry() {
  }

  public LedgerEntry(Long paymentId, Long userId, BigDecimal amount,
      Currency currency, PaymentEventType entryType) {
    this.paymentId = paymentId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.entryType = entryType;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }

  // Getters only - no setters to enforce immutability

  public Long getId() {
    return id;
  }

  public Long getPaymentId() {
    return paymentId;
  }

  public Long getUserId() {
    return userId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return currency;
  }

  public PaymentEventType getEntryType() {
    return entryType;
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

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }
}
