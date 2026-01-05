package com.learn.searchservice.controller;

import com.learn.searchservice.document.PaymentDocument;
import com.learn.searchservice.repository.PaymentSearchRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

  private final PaymentSearchRepository repository;

  public SearchController(PaymentSearchRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/payments")
  public List<PaymentDocument> searchPayments(@RequestParam String query) {
    // Simple full-text search on message
    return repository.findByMessageContaining(query);
  }

  @GetMapping("/payments/user/{userId}")
  public List<PaymentDocument> getPaymentsByUser(@PathVariable Long userId) {
    return repository.findByUserId(userId);
  }

  @GetMapping("/health")
  public String health() {
    return "Search Service is healthy";
  }
}
