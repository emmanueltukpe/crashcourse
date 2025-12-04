package com.learn.sessionservice.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbBean
public class Session {

  private String sessionId;
  private String userId;
  private String data;
  private Instant createdAt;
  private Instant lastAccessedAt;
  private Long ttl;

  public Session() {
  }

  public Session(String sessionId, String userId, String data, Instant createdAt, Instant lastAccessedAt, Long ttl) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.data = data;
    this.createdAt = createdAt;
    this.lastAccessedAt = lastAccessedAt;
    this.ttl = ttl;
  }

  @DynamoDbPartitionKey
  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getLastAccessedAt() {
    return lastAccessedAt;
  }

  public void setLastAccessedAt(Instant lastAccessedAt) {
    this.lastAccessedAt = lastAccessedAt;
  }

  public Long getTtl() {
    return ttl;
  }

  public void setTtl(Long ttl) {
    this.ttl = ttl;
  }
}
