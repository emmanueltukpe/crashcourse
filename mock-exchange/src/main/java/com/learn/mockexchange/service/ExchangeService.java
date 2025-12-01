package com.learn.mockexchange.service;

import com.learn.common.dto.*;
import com.learn.common.enums.Currency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EDUCATIONAL NOTE - Mocking External Services
 * 
 * This service simulates an external cryptocurrency exchange.
 * It's essential for learning and testing because:
 * 1. We don't need real API keys or accounts
 * 2. We can simulate failures to test error handling
 * 3. We have full control over responses for testing
 * 4. No rate limits or costs
 * 
 * In production, you'd replace this with actual API calls to
 * real exchanges like Binance, Coinbase, Kraken, etc.
 */
@Service
public class ExchangeService {

  // Store quotes temporarily (in-memory simulation)
  // In a real exchange, quotes might be valid for 30-60 seconds
  private final Map<String, QuoteResponse> quotes = new ConcurrentHashMap<>();

  private final Random random = new Random();

  /**
   * Get exchange rate between two currencies
   * 
   * EDUCATIONAL NOTE - Exchange Rates
   * These are simplified, fixed rates for educational purposes.
   * Real exchange rates:
   * - Fluctuate constantly based on market supply/demand
   * - Are fetched from price oracles or order books
   * - Differ slightly between exchanges (arbitrage opportunities)
   */
  private BigDecimal getExchangeRate(Currency from, Currency to) {
    if (from == to) {
      return BigDecimal.ONE; // Same currency = 1:1 rate
    }

    // USD to NGN (1 USD = 1500 NGN approximately)
    if (from == Currency.USD && to == Currency.NGN) {
      return new BigDecimal("1500.00");
    }
    if (from == Currency.NGN && to == Currency.USD) {
      return new BigDecimal("0.00067"); // 1/1500
    }

    // USD to USDC (stablecoin pegged 1:1 to USD)
    if (from == Currency.USD && to == Currency.USDC) {
      return BigDecimal.ONE;
    }
    if (from == Currency.USDC && to == Currency.USD) {
      return BigDecimal.ONE;
    }

    // USDC to NGN (via USD rate)
    if (from == Currency.USDC && to == Currency.NGN) {
      return new BigDecimal("1500.00");
    }
    if (from == Currency.NGN && to == Currency.USDC) {
      return new BigDecimal("0.00067");
    }

    throw new IllegalArgumentException("Unsupported currency pair: " + from + " to " + to);
  }

  /**
   * Calculate fees for a conversion
   * 
   * EDUCATIONAL NOTE - Trading Fees
   * Exchanges charge fees in various ways:
   * - Percentage of trade amount (most common)
   * - Fixed fee per trade
   * - Tiered fees based on volume (more trading = lower fees)
   * - Different fees for makers vs takers
   * 
   * We use simple percentage fees:
   * - USD ⇄ USDC: 0.5% (low because stablecoin)
   * - Other conversions: 1%
   */
  private BigDecimal calculateFees(Currency from, Currency to, BigDecimal amount) {
    BigDecimal feePercentage;

    if ((from == Currency.USD && to == Currency.USDC) ||
        (from == Currency.USDC && to == Currency.USD)) {
      feePercentage = new BigDecimal("0.005"); // 0.5%
    } else {
      feePercentage = new BigDecimal("0.01"); // 1%
    }

    return amount.multiply(feePercentage).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Generate a quote for converting currencies
   * 
   * @param from   Source currency
   * @param to     Target currency
   * @param amount Amount to convert
   * @return Quote with rate, fees, and quote ID
   */
  public QuoteResponse getQuote(Currency from, Currency to, BigDecimal amount) {
    // Simulate network latency (100-300ms)
    // Real API calls take time!
    simulateLatency();

    // Occasionally fail (5% chance) to simulate realistic API behavior
    // This helps test error handling in account-service
    if (random.nextInt(100) < 5) {
      return new QuoteResponse(
          null, from, to, amount,
          BigDecimal.ZERO, BigDecimal.ZERO, false,
          null, "Exchange temporarily unavailable");
    }

    // Calculate rate and fees
    BigDecimal rate = getExchangeRate(from, to);
    BigDecimal fees = calculateFees(from, to, amount);

    // Generate unique quote ID
    String quoteId = "quote_" + UUID.randomUUID().toString();

    // Quote expires in 30 seconds (realistic for volatile crypto markets)
    LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(30);

    // Create and store the quote
    QuoteResponse quote = new QuoteResponse(
        quoteId, from, to, amount, rate, fees, true, expiresAt,
        "Quote generated successfully");

    quotes.put(quoteId, quote);

    return quote;
  }

  /**
   * Execute a trade based on a quote
   * 
   * @param quoteId The quote ID from a previous getQuote call
   * @return Result of the trade execution
   */
  public ExecuteTradeResponse executeTrade(String quoteId) {
    // Simulate network latency
    simulateLatency();

    // Check if quote exists
    QuoteResponse quote = quotes.get(quoteId);
    if (quote == null) {
      return new ExecuteTradeResponse(
          false, null, quoteId,
          "Quote not found or expired");
    }

    // Check if quote has expired
    if (quote.getExpiresAt().isBefore(LocalDateTime.now())) {
      quotes.remove(quoteId); // Clean up expired quote
      return new ExecuteTradeResponse(
          false, null, quoteId,
          "Quote has expired");
    }

    // Occasionally fail execution (3% chance) to test rollback behavior
    if (random.nextInt(100) < 3) {
      return new ExecuteTradeResponse(
          false, null, quoteId,
          "Execution failed due to insufficient liquidity");
    }

    // Success! Generate transaction ID
    String transactionId = "tx_" + UUID.randomUUID().toString();

    // Remove the quote (it's been used)
    quotes.remove(quoteId);

    return new ExecuteTradeResponse(
        true, transactionId, quoteId,
        "Trade executed successfully");
  }

  /**
   * Simulate network latency (100-300ms)
   * Real API calls aren't instantaneous!
   */
  private void simulateLatency() {
    try {
      Thread.sleep(100 + random.nextInt(200));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
