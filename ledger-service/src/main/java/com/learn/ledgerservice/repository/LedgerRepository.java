package com.learn.ledgerservice.repository;

import com.learn.ledgerservice.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LedgerEntry entity
 */
@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

  /**
   * Find all ledger entries for a user
   */
  List<LedgerEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

  /**
   * Find ledger entry for a specific payment
   */
  List<LedgerEntry> findByPaymentId(Long paymentId);

  /**
   * Check if a message has already been processed (for idempotency)
   * 
   * EDUCATIONAL NOTE - Idempotency
   * Kafka can deliver the same message more than once due to:
   * - Network issues
   * - Consumer restarts
   * - Kafka rebalancing
   * 
   * We must handle duplicates gracefully:
   * 1. Check if we've seen this messageId before
   * 2. If yes, ignore it (already processed)
   * 3. If no, process it and save the messageId
   * 
   * This ensures "exactly-once" semantics from the consumer's perspective,
   * even though Kafka provides "at-least-once" delivery.
   */
  Optional<LedgerEntry> findByMessageId(String messageId);

  /**
   * Check if message ID exists
   */
  boolean existsByMessageId(String messageId);
}
