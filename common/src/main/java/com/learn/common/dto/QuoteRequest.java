package com.learn.common.dto;

import com.learn.common.enums.Currency;
import java.math.BigDecimal;

/**
 * Request object for getting a quote from the exchange
 * Used by account-service to call the mock-exchange API
 */
public class QuoteRequest {

  private Currency from;
  private Currency to;
  private BigDecimal amount;

  // Default constructor
  public QuoteRequest() {
  }

  // Parameterized constructor
  public QuoteRequest(Currency from, Currency to, BigDecimal amount) {
    this.from = from;
    this.to = to;
    this.amount = amount;
  }

  // Getters and Setters

  public Currency getFrom() {
    return from;
  }

  public void setFrom(Currency from) {
    this.from = from;
  }

  public Currency getTo() {
    return to;
  }

  public void setTo(Currency to) {
    this.to = to;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
