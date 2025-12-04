package com.learn.accountservice.service;

import com.learn.accountservice.client.ExchangeClient;
import com.learn.accountservice.entity.Account;
import com.learn.accountservice.repository.AccountRepository;
import com.learn.common.dto.*;
import com.learn.common.enums.Currency;
import com.learn.common.exception.ExchangeUnavailableException;
import com.learn.common.exception.InsufficientFundsException;
import com.learn.common.exception.ResourceNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EDUCATIONAL NOTE - AccountService with @Transactional
 * 
 * This service contains the CRITICAL business logic for currency conversion.
 * Pay special attention to the @Transactional annotation and how it ensures
 * data consistency (ACID properties).
 * 
 * STAGE 3 ENHANCEMENT - Concurrency Control
 * 
 * We now demonstrate TWO approaches to handling concurrent access:
 * 1. Pessimistic Locking (existing convert method) - Lock the row immediately
 * 2. Optimistic Locking (new convertWithRetry method) - Check for conflicts at
 * commit time
 * 
 * See docs/locking-and-isolation.md for comprehensive explanation.
 */
@Service
public class AccountService {

  private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
  private static final int MAX_RETRY_ATTEMPTS = 3;

  private final AccountRepository accountRepository;
  private final ExchangeClient exchangeClient;

  public AccountService(AccountRepository accountRepository, ExchangeClient exchangeClient) {
    this.accountRepository = accountRepository;
    this.exchangeClient = exchangeClient;
  }

  /**
   * Create a new account for a user
   * 
   * @param userId            The user who owns this account
   * @param initialUsdBalance Initial USD balance to seed the account
   * @return The created account
   */
  @Transactional
  public Account createAccount(Long userId, BigDecimal initialUsdBalance) {
    // Check if account already exists
    if (accountRepository.existsByUserId(userId)) {
      throw new RuntimeException("Account already exists for user " + userId);
    }

    Account account = new Account(userId);
    if (initialUsdBalance != null && initialUsdBalance.compareTo(BigDecimal.ZERO) > 0) {
      account.setUsdBalance(initialUsdBalance);
    }

    return accountRepository.save(account);
  }

  /**
   * Get account by user ID
   */
  @Transactional(readOnly = true)
  public Account getAccountByUserId(Long userId) {
    return accountRepository.findByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found for user " + userId));
  }

  /**
   * Convert currency with ACID guarantees (PESSIMISTIC LOCKING APPROACH)
   * 
   * ═══════════════════════════════════════════════════════════════
   * EDUCATIONAL NOTE - @Transactional and ACID Properties
   * ═══════════════════════════════════════════════════════════════
   * 
   * This is the MOST IMPORTANT method in Stage 1. It demonstrates how
   * 
   * @Transactional ensures ACID properties for financial operations.
   * 
   *                ACID stands for:
   * 
   *                A - ATOMICITY: All-or-nothing
   *                Either the whole operation succeeds, or none of it does.
   *                Example: If the exchange execution fails, the database changes
   *                are rolled back.
   * 
   *                C - CONSISTENCY: Data stays valid
   *                The database goes from one valid state to another.
   *                Example: Balances cannot go negative (we check this).
   * 
   *                I - ISOLATION: Concurrent transactions don't interfere
   *                Multiple users converting simultaneously won't cause incorrect
   *                balances.
   *                We use pessimistic locking (findByUserIdForUpdate) to achieve
   *                this.
   * 
   *                D - DURABILITY: Once committed, changes are permanent
   *                After the method returns successfully, the balance changes are
   *                saved forever.
   * 
   *                How @Transactional works:
   * 
   *                1. Spring starts a database transaction when this method is
   *                called
   *                2. All database operations (read, write) happen within this
   *                transaction
   *                3. If the method completes successfully, Spring COMMITS the
   *                transaction
   *                4. If an exception is thrown, Spring ROLLS BACK the
   *                transaction
   * 
   *                What gets rolled back?
   *                - Database changes (balance updates)
   * 
   *                What does NOT get rolled back?
   *                - External API calls (we already called the exchange)
   *                - Files written to disk
   *                - Messages sent to Kafka (we'll handle this in Stage 2 with
   *                outbox pattern)
   * 
   *                ═══════════════════════════════════════════════════════════════
   */
  @Transactional
  public ConvertResponse convert(Long userId, Currency fromCurrency, Currency toCurrency, BigDecimal amount) {
    // Step 1: Lock and retrieve the account
    // findByUserIdForUpdate uses SELECT ... FOR UPDATE (pessimistic lock)
    // This prevents other transactions from modifying this account until we're done
    Account account = accountRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found for user " + userId));

    // Step 2: Validate sufficient balance
    BigDecimal currentBalance = account.getBalance(fromCurrency);
    if (currentBalance.compareTo(amount) < 0) {
      // Throw exception - this will cause the transaction to rollback
      // (Though we haven't made any changes yet, so nothing to rollback)
      throw new InsufficientFundsException(
          String.format("Insufficient %s balance. Current: %s, Required: %s",
              fromCurrency, currentBalance, amount));
    }

    // Step 3: Get quote from external exchange
    QuoteRequest quoteRequest = new QuoteRequest(fromCurrency, toCurrency, amount);
    QuoteResponse quote = exchangeClient.getQuote(quoteRequest);

    // Step 4: Verify quote is available
    if (!quote.isAvailable()) {
      // Exchange said the trade is not available
      throw new ExchangeUnavailableException("Exchange cannot fulfill this conversion");
    }

    // Step 5: Execute the trade with the exchange
    // IMPORTANT: This is an external system call. If it succeeds but our database
    // update fails, we have a problem! In production, use patterns like:
    // - Idempotency (safe to retry)
    // - Compensation (reverse the trade if our DB fails)
    // - Saga pattern (we'll cover in Stage 2)
    ExecuteTradeRequest executeRequest = new ExecuteTradeRequest(quote.getQuoteId());
    ExecuteTradeResponse executeResponse = exchangeClient.executeTrade(executeRequest);

    // Step 6: Verify execution succeeded
    if (!executeResponse.isSuccess()) {
      // Exchange execution failed - throw exception to rollback
      throw new ExchangeUnavailableException("Trade execution failed: " + executeResponse.getMessage());
    }

    // Step 7: Calculate converted amount
    // Formula: (amount * rate) - fees
    BigDecimal convertedAmount = amount
        .multiply(quote.getRate())
        .subtract(quote.getFees())
        .setScale(2, RoundingMode.HALF_UP); // Round to 2 decimal places

    // Step 8: Update balances atomically
    // These changes are part of the database transaction
    account.debit(fromCurrency, amount); // Subtract from source currency
    account.credit(toCurrency, convertedAmount); // Add to target currency

    // Step 9: Save the account
    // This updates the database row (still within the transaction)
    accountRepository.save(account);

    // Step 10: If we reach here, Spring will COMMIT the transaction
    // The balance changes are now permanent in the database

    // Return success response
    return new ConvertResponse(
        convertedAmount,
        quote.getRate(),
        quote.getFees(),
        fromCurrency,
        toCurrency,
        amount,
        "Conversion successful");

    // NOTE: If any exception is thrown before reaching here,
    // Spring automatically ROLLS BACK all database changes.
    // The account balances will remain unchanged!
  }

  /**
   * ═══════════════════════════════════════════════════════════════
   * STAGE 3 - OPTIMISTIC LOCKING WITH RETRY
   * ═══════════════════════════════════════════════════════════════
   * 
   * Alternative conversion method using OPTIMISTIC LOCKING instead of
   * pessimistic.
   * 
   * Optimistic Locking Strategy:
   * 1. Read the data WITHOUT locking
   * 2. Do your work (calculations, API calls)
   * 3. Try to save - if someone else modified it, you get OptimisticLockException
   * 4. Retry the whole operation
   * 
   * When to use OPTIMISTIC vs PESSIMISTIC:
   * 
   * OPTIMISTIC (this method):
   * ✓ Low contention (conflicts are rare)
   * ✓ Read-heavy workloads
   * ✓ Long-running operations (don't want to hold locks)
   * ✓ Better throughput
   * ✗ Need retry logic
   * 
   * PESSIMISTIC (convert method above):
   * ✓ High contention (many concurrent updates)
   * ✓ Critical operations (can't afford retries)
   * ✓ Short operations (locks held briefly)
   * ✗ Lower throughput
   * ✗ Deadlock potential
   * 
   * How the @Version field works:
   * 1. SELECT * FROM accounts WHERE user_id = ?
   * (account has version = 5)
   * 2. Do calculations...
   * 3. UPDATE accounts SET ..., version = 6 WHERE id = ? AND version = 5
   * If version is still 5: Update succeeds! ✓
   * If version changed to 6: Update fails! → OptimisticLockException
   * 
   * Retry Logic:
   * We automatically retry up to MAX_RETRY_ATTEMPTS times.
   * Each retry re-reads the latest data and tries again.
   */
  public ConvertResponse convertWithRetry(Long userId, Currency fromCurrency, Currency toCurrency, BigDecimal amount) {
    int attempt = 0;

    while (attempt < MAX_RETRY_ATTEMPTS) {
      try {
        attempt++;
        logger.debug("Attempting conversion (attempt {}/{})", attempt, MAX_RETRY_ATTEMPTS);

        // Try the conversion with optimistic locking
        return convertWithOptimisticLocking(userId, fromCurrency, toCurrency, amount);

      } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
        // Optimistic lock failed - someone else modified the account
        logger.warn("Optimistic lock failure on attempt {}/{}: {}",
            attempt, MAX_RETRY_ATTEMPTS, e.getMessage());

        if (attempt >= MAX_RETRY_ATTEMPTS) {
          // Exceeded retry limit - give up
          throw new RuntimeException(
              "Failed to complete conversion after " + MAX_RETRY_ATTEMPTS + " attempts due to concurrent modifications",
              e);
        }

        // Brief pause before retry (exponential backoff)
        try {
          Thread.sleep(50 * attempt); // 50ms, 100ms, 150ms
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Conversion interrupted during retry", ie);
        }

        // Loop will retry
      }
    }

    throw new RuntimeException("Failed to complete conversion - maximum retries exceeded");
  }

  /**
   * Internal method that performs conversion with optimistic locking
   * (No pessimistic lock - relies on @Version field)
   */
  @Transactional
  protected ConvertResponse convertWithOptimisticLocking(Long userId, Currency fromCurrency, Currency toCurrency,
      BigDecimal amount) {
    // Step 1: Read account WITHOUT locking (optimistic approach)
    Account account = accountRepository.findByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found for user " + userId));

    // Step 2: Validate sufficient balance
    BigDecimal currentBalance = account.getBalance(fromCurrency);
    if (currentBalance.compareTo(amount) < 0) {
      throw new InsufficientFundsException(
          String.format("Insufficient %s balance. Current: %s, Required: %s",
              fromCurrency, currentBalance, amount));
    }

    // Step 3-6: Get quote and execute trade (same as pessimistic version)
    QuoteRequest quoteRequest = new QuoteRequest(fromCurrency, toCurrency, amount);
    QuoteResponse quote = exchangeClient.getQuote(quoteRequest);

    if (!quote.isAvailable()) {
      throw new ExchangeUnavailableException("Exchange cannot fulfill this conversion");
    }

    ExecuteTradeRequest executeRequest = new ExecuteTradeRequest(quote.getQuoteId());
    ExecuteTradeResponse executeResponse = exchangeClient.executeTrade(executeRequest);

    if (!executeResponse.isSuccess()) {
      throw new ExchangeUnavailableException("Trade execution failed: " + executeResponse.getMessage());
    }

    // Step 7: Calculate converted amount
    BigDecimal convertedAmount = amount
        .multiply(quote.getRate())
        .subtract(quote.getFees())
        .setScale(2, RoundingMode.HALF_UP);

    // Step 8: Update balances
    account.debit(fromCurrency, amount);
    account.credit(toCurrency, convertedAmount);

    // Step 9: Save account
    // This is where optimistic locking happens!
    // JPA will execute: UPDATE accounts SET ..., version = version + 1 WHERE id = ?
    // AND version = ?
    // If version changed, OptimisticLockException is thrown
    accountRepository.save(account);

    // Return success
    return new ConvertResponse(
        convertedAmount,
        quote.getRate(),
        quote.getFees(),
        fromCurrency,
        toCurrency,
        amount,
        "Conversion successful (optimistic locking)");
  }
}
