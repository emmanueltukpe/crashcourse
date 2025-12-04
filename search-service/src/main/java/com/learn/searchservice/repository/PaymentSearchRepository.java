package com.learn.searchservice.repository;

import com.learn.searchservice.document.PaymentDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentSearchRepository extends ElasticsearchRepository<PaymentDocument, String> {

  // Spring Data Elasticsearch generates queries from method names!

  // Full-text search on message
  List<PaymentDocument> findByMessageContaining(String message);

  // Exact match on user ID
  List<PaymentDocument> findByUserId(Long userId);

  // Exact match on status
  List<PaymentDocument> findByStatus(String status);
}
