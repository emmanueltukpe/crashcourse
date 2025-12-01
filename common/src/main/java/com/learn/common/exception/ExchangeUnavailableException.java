package com.learn.common.exception;

/**
 * Thrown when the external exchange service is unavailable or returns an error
 * This will typically map to HTTP 503 Service Unavailable
 */
public class ExchangeUnavailableException extends RuntimeException {

  public ExchangeUnavailableException(String message) {
    super(message);
  }

  public ExchangeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
