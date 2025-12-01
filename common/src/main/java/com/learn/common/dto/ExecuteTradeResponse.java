package com.learn.common.dto;

/**
 * Response object for trade execution confirmation
 */
public class ExecuteTradeResponse {

  /**
   * Whether the trade was successfully executed
   */
  private boolean success;

  /**
   * Transaction ID for this completed trade
   */
  private String transactionId;

  /**
   * The quote ID that was executed
   */
  private String quoteId;

  private String message;

  // Default constructor
  public ExecuteTradeResponse() {
  }

  // Parameterized constructor
  public ExecuteTradeResponse(boolean success, String transactionId, String quoteId, String message) {
    this.success = success;
    this.transactionId = transactionId;
    this.quoteId = quoteId;
    this.message = message;
  }

  // Getters and Setters

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getQuoteId() {
    return quoteId;
  }

  public void setQuoteId(String quoteId) {
    this.quoteId = quoteId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
