package com.learn.common.enums;

/**
 * EDUCATIONAL NOTE - Payment Status Enum
 * 
 * This enum represents the lifecycle of a payment in our system.
 * In financial systems, modeling state transitions is critical.
 * 
 * Payment Lifecycle:
 * 1. PENDING - Payment created but not yet processed
 * 2. PROCESSING - Payment is being validated/executed
 * 3. COMPLETED - Payment successfully completed
 * 4. FAILED - Payment failed (insufficient funds, validation error, etc.)
 * 5. REFUNDED - Payment was completed but later refunded
 * 
 * State Transition Rules:
 * PENDING → PROCESSING → COMPLETED
 * ↓
 * FAILED
 * 
 * COMPLETED → REFUNDED (only if originally completed)
 * 
 * Why use enums for status?
 * - Type safety: Can't accidentally use invalid status
 * - Compile-time checking: Typos are caught immediately
 * - IDE support: Auto-completion shows all valid values
 * - Clean code: More readable than magic strings
 */
public enum PaymentStatus {
  /**
   * Payment has been created but not yet processed
   */
  PENDING,

  /**
   * Payment is currently being processed
   */
  PROCESSING,

  /**
   * Payment completed successfully
   */
  COMPLETED,

  /**
   * Payment failed (insufficient funds, validation error, etc.)
   */
  FAILED,

  /**
   * Payment was completed but later refunded
   */
  REFUNDED;

  /**
   * Check if this status is a terminal state (won't change again)
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == REFUNDED;
  }

  /**
   * Check if payment was successful
   */
  public boolean isSuccessful() {
    return this == COMPLETED;
  }
}
