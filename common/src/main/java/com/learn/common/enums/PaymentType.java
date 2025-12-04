package com.learn.common.enums;

/**
 * Types of payments supported by the system
 */
public enum PaymentType {
  /**
   * Currency conversion payment
   */
  CONVERSION,

  /**
   * Transfer to another user
   */
  TRANSFER,

  /**
   * Withdrawal to external account
   */
  WITHDRAWAL,

  /**
   * Deposit from external source
   */
  DEPOSIT,

  /**
   * Refund of a previous payment
   */
  REFUND
}
