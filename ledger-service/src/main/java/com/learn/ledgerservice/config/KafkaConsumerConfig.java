package com.learn.ledgerservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * EDUCATIONAL NOTE - Kafka Consumer Configuration
 * 
 * This configures how our application consumes (receives) messages from Kafka.
 * 
 * Key Concepts:
 * - Consumer: Application that reads messages from Kafka
 * - Consumer Group: Set of consumers that share workload
 * - Offset: Position in the topic (which messages have been read)
 * - Deserializer: Converts bytes back to Java objects
 * 
 * Commit Strategy:
 * We use auto-commit for simplicity, but in production you might use
 * manual commits for better control over when offsets are committed.
 */
@Configuration
public class KafkaConsumerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  /**
   * Consumer configuration properties
   */
  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> config = new HashMap<>();

    // Kafka broker addresses
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    // Consumer group ID
    // All consumers with same group ID share the workload
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "ledger-service");

    // Key deserializer
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    // Value deserializer
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    // Start from earliest message if no offset exists
    // "earliest" = read from beginning
    // "latest" = read only new messages
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Enable auto-commit (commits offset automatically after processing)
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

    // Auto-commit interval (5 seconds)
    config.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);

    return new DefaultKafkaConsumerFactory<>(config);
  }

  /**
   * Kafka listener container factory
   * Used by @KafkaListener
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    return factory;
  }
}
