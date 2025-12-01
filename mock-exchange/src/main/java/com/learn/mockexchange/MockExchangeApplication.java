package com.learn.mockexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock Exchange Service
 * 
 * This service simulates a third-party cryptocurrency exchange API.
 * In a real application, you would integrate with actual exchanges like
 * Binance, Coinbase, or Kraken. For learning purposes, we mock it.
 */
@SpringBootApplication
public class MockExchangeApplication {

  public static void main(String[] args) {
    SpringApplication.run(MockExchangeApplication.class, args);
  }
}
