package com.learn.paymentservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * EDUCATIONAL NOTE - Kafka Producer Configuration
 * 
 * This configures how our application produces (sends) messages to Kafka.
 * 
 * Key Concepts:
 * - Producer: Application that sends messages to Kafka
 * - Topic: Named stream of messages (like a queue or channel)
 * - Partition: Topics are split into partitions for scalability
 * - Key: Optional message key (used for partitioning)
 * - Value: The actual message content
 * 
 * Serialization:
 * Kafka stores bytes, so we need to convert Java objects to bytes.
 * We use StringSerializer for both key and value (JSON strings).
 */
@Configuration
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  /**
   * Producer configuration properties
   */
  @Bean
  public ProducerFactory<String, String> producerFactory() {
    Map<String, Object> config = new HashMap<>();

    // Kafka broker addresses
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    // Key serializer (converts Java String to bytes)
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // Value serializer (converts Java String to bytes)
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // Acknowledgment level
    // "all" = wait for all replicas to acknowledge (most reliable)
    // "1" = wait for leader only (faster, less reliable)
    // "0" = don't wait (fastest, least reliable)
    config.put(ProducerConfig.ACKS_CONFIG, "all");

    // Retries if send fails
    config.put(ProducerConfig.RETRIES_CONFIG, 3);

    // Ensure messages are sent in order
    config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

    return new DefaultKafkaProducerFactory<>(config);
  }

  /**
   * KafkaTemplate for sending messages
   * This is what we inject into services
   */
  @Bean
  public KafkaTemplate<String, String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }
}
