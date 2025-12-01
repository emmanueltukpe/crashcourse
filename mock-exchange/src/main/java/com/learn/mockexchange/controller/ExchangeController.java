package com.learn.mockexchange.controller;

import com.learn.common.dto.*;
import com.learn.common.enums.Currency;
import com.learn.mockexchange.service.ExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller for the mock exchange API
 * 
 * This exposes endpoints that account-service calls to get quotes and execute
 * trades.
 */
@RestController
@RequestMapping("/api")
public class ExchangeController {

  private final ExchangeService exchangeService;

  public ExchangeController(ExchangeService exchangeService) {
    this.exchangeService = exchangeService;
  }

  /**
   * Get a quote for currency conversion
   * 
   * GET /api/quote?from=USD&to=NGN&amount=100
   * 
   * Example response:
   * {
   * "quoteId": "quote_abc123",
   * "from": "USD",
   * "to": "NGN",
   * "amount": 100.00,
   * "rate": 1500.00,
   * "fees": 1.00,
   * "available": true,
   * "expiresAt": "2025-12-01T11:30:00",
   * "message": "Quote generated successfully"
   * }
   */
  @GetMapping("/quote")
  public ResponseEntity<QuoteResponse> getQuote(
      @RequestParam Currency from,
      @RequestParam Currency to,
      @RequestParam BigDecimal amount) {

    QuoteResponse quote = exchangeService.getQuote(from, to, amount);
    return ResponseEntity.ok(quote);
  }

  /**
   * Execute a trade based on a quote
   * 
   * POST /api/execute
   * Body: {"quoteId": "quote_abc123"}
   * 
   * Example response:
   * {
   * "success": true,
   * "transactionId": "tx_xyz789",
   * "quoteId": "quote_abc123",
   * "message": "Trade executed successfully"
   * }
   */
  @PostMapping("/execute")
  public ResponseEntity<ExecuteTradeResponse> executeTrade(@RequestBody ExecuteTradeRequest request) {
    ExecuteTradeResponse response = exchangeService.executeTrade(request.getQuoteId());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Mock exchange is running");
  }
}
