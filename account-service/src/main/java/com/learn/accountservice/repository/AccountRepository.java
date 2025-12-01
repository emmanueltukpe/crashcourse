package com.learn.accountservice.repository;

import com.learn.accountservice.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository for Account entity
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

  /**
   * Find account by user ID
   */
  Optional<Account> findByUserId(Long userId);

  /**
   * Find account by user ID with pessimistic write lock
   * 
   * EDUCATIONAL NOTE - Pessimistic Locking
   * 
   * @Lock(LockModeType.PESSIMISTIC_WRITE) tells the database to lock the row
   *                                       when it's selected, preventing other
   *                                       transactions from reading or writing
   *                                       it until this transaction completes.
   * 
   *                                       SQL generated (approximately):
   *                                       SELECT * FROM accounts WHERE user_id =
   *                                       ? FOR UPDATE
   * 
   *                                       "FOR UPDATE" tells the database: "I'm
   *                                       going to modify this row, lock it!"
   * 
   *                                       When to use pessimistic locking:
   *                                       ✓ Critical financial operations where
   *                                       you can't afford conflicts
   *                                       ✓ High contention scenarios (many users
   *                                       updating the same resource)
   *                                       ✗ Long-running transactions (holds
   *                                       locks too long)
   *                                       ✗ High-read, low-write scenarios
   *                                       (optimistic locking is better)
   * 
   *                                       Trade-offs:
   *                                       + Guarantees no concurrent
   *                                       modifications
   *                                       - Reduced concurrency (other
   *                                       transactions must wait)
   *                                       - Potential for deadlocks
   * 
   *                                       For Stage 1, we use this to ensure
   *                                       conversions are atomic.
   *                                       In Stage 3, we'll explore optimistic
   *                                       locking as an alternative.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Account a WHERE a.userId = :userId")
  Optional<Account> findByUserIdForUpdate(@Param("userId") Long userId);

  /**
   * Check if an account exists for a user
   */
  boolean existsByUserId(Long userId);
}
