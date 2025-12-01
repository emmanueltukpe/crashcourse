package com.learn.common.dto;

/**
 * Request to execute a trade based on a previously received quote
 */
public class ExecuteTradeRequest {

  /**
   * The quote ID from a previous QuoteResponse
   * This ensures we execute at the rate that was quoted
   */
  private String quoteId;

  // Default constructor
  public ExecuteTradeRequest() {
  }

  // Parameterized constructor
  public ExecuteTradeRequest(String quoteId) {
    this.quoteId = quoteId;
  }

  // Getters and Setters

  public String getQuoteId() {
    return quoteId;
  }

  public void setQuoteId(String quoteId) {
    this.quoteId = quoteId;
  }
}
