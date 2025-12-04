package com.learn.searchservice.event;

import com.learn.common.dto.PaymentEvent;
import com.learn.searchservice.document.PaymentDocument;
import com.learn.searchservice.repository.PaymentSearchRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentEventListener {

  private final PaymentSearchRepository repository;

  public PaymentEventListener(PaymentSearchRepository repository) {
    this.repository = repository;
  }

  /**
   * Consume payment events and index them in Elasticsearch
   *
   * EDUCATIONAL NOTE - CQRS & Event Sourcing
   *
   * This is the "Read Model" update side of CQRS.
   * 1. Payment Service (Write Model) publishes event
   * 2. Search Service (Read Model) consumes event
   * 3. Search Service updates Elasticsearch
   *
   * This decouples the write path (Postgres) from the read path (Elasticsearch).
   */
  @KafkaListener(topics = "payment-events", groupId = "search-service-group")
  public void handlePaymentEvent(PaymentEvent event) {
    System.out.println("Received payment event: " + event);

    // Map event to document
    PaymentDocument document = new PaymentDocument(
        event.getPaymentId().toString(),
        event.getUserId(),
        event.getAmount(),
        event.getCurrency().name(),
        event.getEventType().name(),
        event.getMessage() != null ? event.getMessage() : "",
        Instant.now());

    // Save to Elasticsearch
    repository.save(document);
    System.out.println("Indexed payment " + event.getPaymentId() + " in Elasticsearch");
  }
}
