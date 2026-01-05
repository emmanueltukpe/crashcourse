package com.learn.searchservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment Document for Elasticsearch
 *
 * EDUCATIONAL NOTE - Elasticsearch Mapping
 *
 * @Document(indexName = "payments") tells Spring Data to store this in the
 *                     "payments" index.
 *
 *                     Field Types:
 *                     - Keyword: Exact match (good for IDs, status, enums). Not
 *                     analyzed.
 *                     - Text: Full-text search (analyzed, tokenized). Good for
 *                     descriptions.
 *                     - Double/Date: Standard types for range queries.
 */
@Document(indexName = "payments")
public class PaymentDocument {

  @Id
  private String id; // Payment ID

  @Field(type = FieldType.Keyword)
  private Long userId;

  @Field(type = FieldType.Double)
  private BigDecimal amount;

  @Field(type = FieldType.Keyword)
  private String currency;

  @Field(type = FieldType.Keyword)
  private String eventType;

  @Field(type = FieldType.Text) // Analyzed for full-text search
  private String message;

  @Field(type = FieldType.Date)
  private Instant createdAt;

  public PaymentDocument() {
  }

  public PaymentDocument(String id, Long userId, BigDecimal amount, String currency, String eventType, String message,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.eventType = eventType;
    this.message = message;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
