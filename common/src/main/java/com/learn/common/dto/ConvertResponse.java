package com.learn.common.dto;

import com.learn.common.enums.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response object returned after a successful currency conversion
 * 
 * EDUCATIONAL NOTE - Response DTOs
 * Response DTOs define what data we send back to the client.
 * They should contain all information the client needs to know about the
 * operation.
 */
public class ConvertResponse {

  /**
   * The converted amount in the target currency
   */
  private BigDecimal convertedAmount;

  /**
   * Exchange rate used for the conversion
   */
  private BigDecimal exchangeRate;

  /**
   * Fees charged for the conversion
   */
  private BigDecimal fees;

  /**
   * Source currency
   */
  private Currency fromCurrency;

  /**
   * Target currency
   */
  private Currency toCurrency;

  /**
   * Original amount requested
   */
  private BigDecimal originalAmount;

  /**
   * Timestamp when the conversion was completed
   */
  private LocalDateTime timestamp;

  /**
   * Success message
   */
  private String message;

  // Default constructor
  public ConvertResponse() {
    this.timestamp = LocalDateTime.now();
  }

  // All-args constructor
  public ConvertResponse(BigDecimal convertedAmount, BigDecimal exchangeRate, BigDecimal fees,
      Currency fromCurrency, Currency toCurrency, BigDecimal originalAmount,
      String message) {
    this.convertedAmount = convertedAmount;
    this.exchangeRate = exchangeRate;
    this.fees = fees;
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
    this.originalAmount = originalAmount;
    this.timestamp = LocalDateTime.now();
    this.message = message;
  }

  // Getters and Setters

  public BigDecimal getConvertedAmount() {
    return convertedAmount;
  }

  public void setConvertedAmount(BigDecimal convertedAmount) {
    this.convertedAmount = convertedAmount;
  }

  public BigDecimal getExchangeRate() {
    return exchangeRate;
  }

  public void setExchangeRate(BigDecimal exchangeRate) {
    this.exchangeRate = exchangeRate;
  }

  public BigDecimal getFees() {
    return fees;
  }

  public void setFees(BigDecimal fees) {
    this.fees = fees;
  }

  public Currency getFromCurrency() {
    return fromCurrency;
  }

  public void setFromCurrency(Currency fromCurrency) {
    this.fromCurrency = fromCurrency;
  }

  public Currency getToCurrency() {
    return toCurrency;
  }

  public void setToCurrency(Currency toCurrency) {
    this.toCurrency = toCurrency;
  }

  public BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  public void setOriginalAmount(BigDecimal originalAmount) {
    this.originalAmount = originalAmount;
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
}
