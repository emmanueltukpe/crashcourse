package com.learn.authservice.repository;

import com.learn.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * EDUCATIONAL NOTE - Spring Data JPA Repositories
 * 
 * A Repository is an interface that provides database access methods.
 * By extending JpaRepository, Spring Data JPA automatically implements
 * common database operations for us - we don't need to write SQL!
 * 
 * JpaRepository<User, Long> means:
 * - User: The entity type this repository manages
 * - Long: The type of the entity's primary key (id field)
 * 
 * FREE METHODS you get automatically (no code needed!):
 * - save(User user) - Insert or update a user
 * - findById(Long id) - Find user by ID
 * - findAll() - Get all users
 * - deleteById(Long id) - Delete a user
 * - count() - Count number of users
 * - existsById(Long id) - Check if a user exists
 * 
 * CUSTOM QUERIES:
 * You can define custom query methods just by declaring them!
 * Spring Data JPA parses the method name and generates the query.
 * 
 * Example: findByEmail → SELECT * FROM users WHERE email = ?
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Find a user by email address
   * 
   * Spring Data JPA automatically implements this by parsing the method name:
   * - "findBy" - This is a query method
   * - "Email" - Query by the email field
   * 
   * Generated SQL (approximately):
   * SELECT * FROM users WHERE email = ?
   * 
   * Returns Optional<User> because the user might not exist.
   * Optional is a Java 8+ feature to handle potential null values safely.
   * 
   * Usage:
   * Optional<User> userOpt = userRepository.findByEmail("test@example.com");
   * if (userOpt.isPresent()) {
   * User user = userOpt.get();
   * // use the user
   * } else {
   * // user not found
   * }
   */
  Optional<User> findByEmail(String email);

  /**
   * Check if a user with the given email exists
   * 
   * Spring Data JPA generates:
   * SELECT COUNT(*) > 0 FROM users WHERE email = ?
   * 
   * This is more efficient than findByEmail if you only need to check existence.
   */
  boolean existsByEmail(String email);
}
