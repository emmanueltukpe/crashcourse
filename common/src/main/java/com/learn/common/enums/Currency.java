package com.learn.common.enums;

/**
 * EDUCATIONAL NOTE - Enums in Java
 * 
 * An enum (enumeration) is a special Java type used to define collections of
 * constants.
 * Think of it as a fixed set of allowed values.
 * 
 * Why use enums?
 * 1. Type safety - You can't accidentally use an invalid currency
 * 2. Compile-time checking - Typos are caught before runtime
 * 3. Readable code - Currency.USD is clearer than the string "USD"
 * 
 * Example usage:
 * Currency from = Currency.USD;
 * Currency to = Currency.NGN;
 */
public enum Currency {
  /**
   * United States Dollar
   */
  USD,

  /**
   * Nigerian Naira
   */
  NGN,

  /**
   * USD Coin - a stablecoin pegged to the US Dollar
   * In real applications, this would represent a cryptocurrency token
   */
  USDC;

  /**
   * Helper method to check if a currency is a fiat currency (government-issued
   * money)
   * 
   * @return true if this is fiat currency (USD or NGN), false if crypto (USDC)
   */
  public boolean isFiat() {
    return this == USD || this == NGN;
  }

  /**
   * Helper method to check if a currency is a cryptocurrency/stablecoin
   * 
   * @return true if this is a cryptocurrency (USDC), false otherwise
   */
  public boolean isCrypto() {
    return this == USDC;
  }
}
