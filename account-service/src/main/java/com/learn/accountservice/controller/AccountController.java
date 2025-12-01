package com.learn.accountservice.controller;

import com.learn.accountservice.entity.Account;
import com.learn.accountservice.service.AccountService;
import com.learn.common.dto.ConvertRequest;
import com.learn.common.dto.ConvertResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST Controller for account operations
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  /**
   * Create a new account
   * 
   * POST /api/v1/accounts
   * Body: {"userId": 1, "initialUsdBalance": 1000.00}
   */
  @PostMapping
  public ResponseEntity<Account> createAccount(@RequestBody CreateAccountRequest request) {
    try {
      Account account = accountService.createAccount(
          request.getUserId(),
          request.getInitialUsdBalance());
      return ResponseEntity.status(HttpStatus.CREATED).body(account);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }
  }

  /**
   * Get account details by user ID
   * 
   * GET /api/v1/accounts/{userId}
   */
  @GetMapping("/{userId}")
  public ResponseEntity<Account> getAccount(@PathVariable Long userId) {
    try {
      Account account = accountService.getAccountByUserId(userId);
      return ResponseEntity.ok(account);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }
  }

  /**
   * Convert currency
   * 
   * POST /api/v1/accounts/convert
   * Body: {"userId": 1, "fromCurrency": "USD", "toCurrency": "NGN", "amount":
   * 100.00}
   */
  @PostMapping("/convert")
  public ResponseEntity<ConvertResponse> convert(@Valid @RequestBody ConvertRequest request) {
    try {
      ConvertResponse response = accountService.convert(
          request.getUserId(),
          request.getFromCurrency(),
          request.getToCurrency(),
          request.getAmount());
      return ResponseEntity.ok(response);
    } catch (RuntimeException e) {
      // In real app, use @ControllerAdvice for better error handling
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Account service is running");
  }

  // Inner DTO for create account request
  public static class CreateAccountRequest {
    private Long userId;
    private BigDecimal initialUsdBalance;

    public Long getUserId() {
      return userId;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public BigDecimal getInitialUsdBalance() {
      return initialUsdBalance;
    }

    public void setInitialUsdBalance(BigDecimal initialUsdBalance) {
      this.initialUsdBalance = initialUsdBalance;
    }
  }
}
