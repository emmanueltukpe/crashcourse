package com.learn.common.dto;

import com.learn.common.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * EDUCATIONAL NOTE - DTOs (Data Transfer Objects)
 * 
 * A DTO is a simple object that carries data between processes or layers.
 * It typically contains only fields and getters/setters, no business logic.
 * 
 * Why use DTOs?
 * 1. Decoupling - API contracts are separate from database entities
 * 2. Validation - We can validate incoming requests before processing
 * 3. Security - We don't expose internal entity structure to clients
 * 
 * This ConvertRequest represents the data needed to convert between currencies.
 */
public class ConvertRequest {

  /**
   * The user ID making the conversion request
   * 
   * @NotNull - This field is required (validation annotation)
   */
  @NotNull(message = "User ID is required")
  private Long userId;

  /**
   * Source currency to convert from (e.g., USD)
   * 
   * @NotNull - This field is required
   */
  @NotNull(message = "Source currency is required")
  private Currency fromCurrency;

  /**
   * Target currency to convert to (e.g., NGN)
   * 
   * @NotNull - This field is required
   */
  @NotNull(message = "Target currency is required")
  private Currency toCurrency;

  /**
   * Amount to convert
   * 
   * @NotNull - This field is required
   * @DecimalMin - Must be greater than 0
   * 
   *             EDUCATIONAL NOTE - BigDecimal
   *             We use BigDecimal for money instead of double/float because:
   *             - double/float have precision issues (0.1 + 0.2 != 0.3)
   *             - Financial calculations require exact decimal arithmetic
   *             - BigDecimal provides precise control over rounding
   */
  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  // Default constructor (required for JSON deserialization)
  public ConvertRequest() {
  }

  // Parameterized constructor for easy object creation
  public ConvertRequest(Long userId, Currency fromCurrency, Currency toCurrency, BigDecimal amount) {
    this.userId = userId;
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
    this.amount = amount;
  }

  // Getters and Setters
  // These allow external code to access and modify the private fields

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
