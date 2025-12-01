package com.learn.common.dto;

import com.learn.common.enums.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response object containing exchange quote details
 * Returned by the mock-exchange service
 * 
 * EDUCATIONAL NOTE - API Design
 * When designing APIs (especially financial ones), include:
 * 1. All the data the client needs to make a decision (rate, fees, expiry)
 * 2. A unique identifier (quoteId) to reference this quote later
 * 3. Expiry time so quotes don't stay valid forever
 * 4. Status/availability flags for error handling
 */
public class QuoteResponse {

  /**
   * Unique identifier for this quote
   * When executing the trade, we'll send this ID back
   */
  private String quoteId;

  private Currency from;
  private Currency to;

  /**
   * Original amount requested
   */
  private BigDecimal amount;

  /**
   * Exchange rate (e.g., 1 USD = 1500 NGN means rate = 1500)
   */
  private BigDecimal rate;

  /**
   * Fees to be charged for this conversion
   */
  private BigDecimal fees;

  /**
   * Whether the exchange can fulfill this quote
   */
  private boolean available;

  /**
   * When this quote expires
   * In real systems, quotes are only valid for a short time (e.g., 30 seconds)
   * to prevent arbitrage from stale pricing
   */
  private LocalDateTime expiresAt;

  private String message;

  // Default constructor
  public QuoteResponse() {
  }

  // All-args constructor
  public QuoteResponse(String quoteId, Currency from, Currency to, BigDecimal amount,
      BigDecimal rate, BigDecimal fees, boolean available,
      LocalDateTime expiresAt, String message) {
    this.quoteId = quoteId;
    this.from = from;
    this.to = to;
    this.amount = amount;
    this.rate = rate;
    this.fees = fees;
    this.available = available;
    this.expiresAt = expiresAt;
    this.message = message;
  }

  // Getters and Setters

  public String getQuoteId() {
    return quoteId;
  }

  public void setQuoteId(String quoteId) {
    this.quoteId = quoteId;
  }

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

  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = rate;
  }

  public BigDecimal getFees() {
    return fees;
  }

  public void setFees(BigDecimal fees) {
    this.fees = fees;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
