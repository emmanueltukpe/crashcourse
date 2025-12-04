package com.learn.common.enums;

/**
 * EDUCATIONAL NOTE - Event Types for Event-Driven Architecture
 * 
 * In an event-driven system, services communicate by publishing and
 * consuming events. This enum defines the types of payment events.
 * 
 * Event-Driven Architecture:
 * - Services are loosely coupled
 * - Communication is asynchronous via message broker (Kafka)
 * - Events represent facts that happened ("PaymentInitiated")
 * - Multiple services can react to the same event
 * 
 * Example Flow:
 * 1. Payment Service publishes PAYMENT_INITIATED
 * 2. Ledger Service consumes and creates entry
 * 3. Ledger Service publishes LEDGER_RECORDED
 * 4. Payment Service consumes and updates status
 */
public enum PaymentEventType {
  /**
   * Payment has been initiated by user
   * Published by: payment-service
   * Consumed by: ledger-service
   */
  PAYMENT_INITIATED,

  /**
   * Payment is being processed
   * Published by: payment-service
   */
  PAYMENT_PROCESSING,

  /**
   * Payment completed successfully
   * Published by: payment-service
   * Consumed by: notification-service (future)
   */
  PAYMENT_COMPLETED,

  /**
   * Payment failed
   * Published by: payment-service
   * Consumed by: ledger-service (for rollback)
   */
  PAYMENT_FAILED,

  /**
   * Payment refunded
   * Published by: payment-service
   * Consumed by: ledger-service, account-service
   */
  PAYMENT_REFUNDED,

  /**
   * Ledger entry recorded
   * Published by: ledger-service
   * Consumed by: payment-service (to complete saga)
   */
  LEDGER_RECORDED,

  /**
   * Ledger entry failed
   * Published by: ledger-service
   * Consumed by: payment-service (to trigger compensating action)
   */
  LEDGER_FAILED
}
