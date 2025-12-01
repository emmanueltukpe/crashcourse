package com.learn.accountservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - Account Entity with Multiple Balances
 * 
 * This entity represents a user's account with balances in multiple currencies.
 * In a real payment system, you might have separate tables for each currency
 * or a more complex structure, but for learning, we keep it simple with
 * three columns for the three supported currencies.
 * 
 * IMPORTANT: For financial applications, we use BigDecimal (not double/float)
 * to avoid precision issues that could cause money to be lost or gained
 * incorrectly.
 */
@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Reference to the user who owns this account
   * This links to the User entity in auth-service
   * 
   * Note: In a microservices architecture, we typically don't use foreign keys
   * across services. We store userId as a simple Long field, not a JPA
   * relationship.
   */
  @Column(nullable = false, unique = true)
  private Long userId;

  /**
   * USD balance
   * 
   * precision = 19: Total number of digits (before and after decimal point)
   * scale = 2: Number of digits after decimal point
   * 
   * Example: 999999999999999.99 (max value with precision=19, scale=2)
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal usdBalance = BigDecimal.ZERO;

  /**
   * Nigerian Naira balance
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal ngnBalance = BigDecimal.ZERO;

  /**
   * Stablecoin (USDC) balance
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal stablecoinBalance = BigDecimal.ZERO;

  /**
   * Version field for optimistic locking
   * 
   * EDUCATIONAL NOTE - Optimistic Locking
   * 
   * @Version tells JPA to use optimistic locking. How it works:
   *          1. When you read an Account, JPA remembers the version number
   *          2. When you update and save, JPA includes the version in the WHERE
   *          clause:
   *          UPDATE accounts SET ..., version = version + 1 WHERE id = ? AND
   *          version = ?
   *          3. If another transaction modified the row (version changed), update
   *          fails
   *          4. JPA throws OptimisticLockException which you can catch and retry
   * 
   *          This prevents lost updates when two transactions try to modify the
   *          same row.
   *          We'll use this more in Stage 3 for handling concurrent conversions.
   */
  @Version
  private Long version;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Default constructor required by JPA
  public Account() {
  }

  // Constructor for creating new accounts
  public Account(Long userId) {
    this.userId = userId;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  /**
   * Helper method to get balance for a specific currency
   */
  public BigDecimal getBalance(com.learn.common.enums.Currency currency) {
    return switch (currency) {
      case USD -> usdBalance;
      case NGN -> ngnBalance;
      case USDC -> stablecoinBalance;
    };
  }

  /**
   * Helper method to debit (subtract from) a specific currency
   * 
   * EDUCATIONAL NOTE - Business Logic in Entities
   * Some developers prefer to keep entities as "dumb" data holders.
   * Others add business logic like these helper methods.
   * For learning, we add them here to make the service code cleaner.
   */
  public void debit(com.learn.common.enums.Currency currency, BigDecimal amount) {
    switch (currency) {
      case USD -> usdBalance = usdBalance.subtract(amount);
      case NGN -> ngnBalance = ngnBalance.subtract(amount);
      case USDC -> stablecoinBalance = stablecoinBalance.subtract(amount);
    }
  }

  /**
   * Helper method to credit (add to) a specific currency
   */
  public void credit(com.learn.common.enums.Currency currency, BigDecimal amount) {
    switch (currency) {
      case USD -> usdBalance = usdBalance.add(amount);
      case NGN -> ngnBalance = ngnBalance.add(amount);
      case USDC -> stablecoinBalance = stablecoinBalance.add(amount);
    }
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

  public BigDecimal getUsdBalance() {
    return usdBalance;
  }

  public void setUsdBalance(BigDecimal usdBalance) {
    this.usdBalance = usdBalance;
  }

  public BigDecimal getNgnBalance() {
    return ngnBalance;
  }

  public void setNgnBalance(BigDecimal ngnBalance) {
    this.ngnBalance = ngnBalance;
  }

  public BigDecimal getStablecoinBalance() {
    return stablecoinBalance;
  }

  public void setStablecoinBalance(BigDecimal stablecoinBalance) {
    this.stablecoinBalance = stablecoinBalance;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
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
