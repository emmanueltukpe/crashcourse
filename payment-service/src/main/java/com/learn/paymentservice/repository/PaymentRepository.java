package com.learn.paymentservice.repository;

import com.learn.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Payment entity
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  /**
   * Find all payments for a specific user
   */
  List<Payment> findByUserId(Long userId);

  /**
   * Find payments by user and status
   */
  List<Payment> findByUserIdAndStatus(Long userId, com.learn.common.enums.PaymentStatus status);
}
