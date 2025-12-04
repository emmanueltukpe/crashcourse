package com.learn.sessionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

  @Value("${aws.dynamodb.endpoint}")
  private String dynamoDbEndpoint;

  @Value("${aws.dynamodb.region}")
  private String awsRegion;

  @Value("${aws.dynamodb.access-key}")
  private String accessKey;

  @Value("${aws.dynamodb.secret-key}")
  private String secretKey;

  @Bean
  public DynamoDbClient dynamoDbClient() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create(dynamoDbEndpoint))
        .region(Region.of(awsRegion))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }

  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(client)
        .build();
  }
}
