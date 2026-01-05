package com.learn.accountservice.client;

import com.learn.common.dto.*;
import com.learn.common.exception.ExchangeUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.Objects;

/**
 * EDUCATIONAL NOTE - HTTP Clients and WebClient
 * 
 * This client calls the mock-exchange service to get quotes and execute trades.
 * We use WebClient (Spring's reactive HTTP client) instead of the older
 * RestTemplate.
 * 
 * WebClient advantages:
 * - Non-blocking / reactive (better performance for high throughput)
 * - Modern API with builder pattern
 * - Better error handling
 * 
 * For this educational project, we use it in a "blocking" mode (.block())
 * which makes it behave like synchronous HTTP calls for simpler understanding.
 * In production high-throughput scenarios, you'd use the reactive features
 * fully.
 */
@Component
public class ExchangeClient {

  private final WebClient webClient;
  /**
   * Constructor with dependency injection
   * 
   * @Value reads the mock-exchange URL from application.yml
   *        If not specified, defaults to http://localhost:8084
   */
  public ExchangeClient(@Value("${exchange.service.url:http://localhost:8084}") String exchangeUrl) {
    Objects.requireNonNull(exchangeUrl, "exchange.service.url must not be null");
    this.webClient = WebClient.builder()
        .baseUrl(exchangeUrl)
        .build();
  }
  

  /**
   * Get a quote from the exchange
   * 
   * @param request Quote request with from/to currencies and amount
   * @return Quote response with rate and fees
   * @throws ExchangeUnavailableException if the exchange service is down or
   *                                      returns an error
   */
  public QuoteResponse getQuote(QuoteRequest request) {
    try {
      return webClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/api/quote")
              .queryParam("from", request.getFrom())
              .queryParam("to", request.getTo())
              .queryParam("amount", request.getAmount())
              .build())
          .retrieve()
          .bodyToMono(QuoteResponse.class)
          .block(); // Block to make this synchronous for Stage 1 simplicity
    } catch (WebClientResponseException e) {
      throw new ExchangeUnavailableException(
          "Failed to get quote from exchange: " + e.getMessage());
    } catch (Exception e) {
      throw new ExchangeUnavailableException(
          "Exchange service unavailable: " + e.getMessage());
    }
  }

  /**
   * Execute a trade based on a quote
   * 
   * @param request Execute request with quote ID
   * @return Execute response confirming the trade
   * @throws ExchangeUnavailableException if execution fails
   */
  public ExecuteTradeResponse executeTrade(ExecuteTradeRequest request) {
    Objects.requireNonNull(request, "ExecuteTradeRequest must not be null");
    try {
      return webClient.post()
          .uri("/api/execute")
          .bodyValue(request)
          .retrieve()
          .bodyToMono(ExecuteTradeResponse.class)
          .block();
    } catch (WebClientResponseException e) {
      throw new ExchangeUnavailableException(
          "Failed to execute trade: " + e.getMessage());
    } catch (Exception e) {
      throw new ExchangeUnavailableException(
          "Exchange service unavailable: " + e.getMessage());
    }
  }
}
