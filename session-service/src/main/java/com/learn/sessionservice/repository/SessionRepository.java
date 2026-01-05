package com.learn.sessionservice.repository;

import com.learn.sessionservice.entity.Session;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class SessionRepository {

  private final DynamoDbEnhancedClient enhancedClient;
  private final DynamoDbTable<Session> sessionTable;

  public SessionRepository(DynamoDbEnhancedClient enhancedClient) {
    this.enhancedClient = enhancedClient;
    this.sessionTable = enhancedClient.table("Sessions", TableSchema.fromBean(Session.class));

    // Create table if not exists (for local dev)
    try {
      sessionTable.createTable();
    } catch (Exception e) {
      // Table might already exist, ignore
    }
  }

  public void save(Session session) {
    sessionTable.putItem(session);
  }

  public Session findById(String sessionId) {
    return sessionTable.getItem(Key.builder().partitionValue(sessionId).build());
  }

  public void delete(String sessionId) {
    sessionTable.deleteItem(Key.builder().partitionValue(sessionId).build());
  }
}
