package com.learn.authservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * EDUCATIONAL NOTE - JPA Entities
 * 
 * An Entity represents a table in the database. Each instance of this class
 * represents a row in the "users" table.
 * 
 * JPA (Jakarta Persistence API) annotations tell Spring/Hibernate how to
 * map this Java class to database tables and columns.
 * 
 * Key annotations:
 * - @Entity: Marks this class as a JPA entity (database table)
 * - @Table: Specifies the table name (optional, defaults to class name)
 * - @Id: Marks the primary key field
 * - @GeneratedValue: Tells the database to auto-generate this value
 * - @Column: Maps fields to specific columns (optional, defaults to field name)
 */
@Entity
@Table(name = "users")
public class User {

  /**
   * Primary key - uniquely identifies each user
   * 
   * @Id marks this as the primary key
   * @GeneratedValue with IDENTITY strategy means the database will auto-increment
   *                 this value
   *                 PostgreSQL uses SERIAL type for this
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * User's email address - used for login
   * 
   * @Column annotations configure column properties:
   *         - unique = true: Database enforces that each email must be unique
   *         - nullable = false: Database enforces that this cannot be null
   */
  @Column(unique = true, nullable = false, length = 255)
  private String email;

  /**
   * Hashed password - NEVER store passwords in plain text!
   * 
   * SECURITY NOTE: We will use BCrypt to hash passwords.
   * BCrypt is a one-way hash function - you can't reverse it to get the original
   * password.
   * When a user logs in, we hash their input and compare it to the stored hash.
   */
  @Column(nullable = false)
  private String passwordHash;

  /**
   * When this user was created
   * 
   * @Column(updatable = false) means this value won't change after initial insert
   */
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * When this user was last updated
   */
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Default constructor required by JPA
   * JPA uses reflection to create instances of this class
   */
  public User() {
  }

  /**
   * Convenience constructor for creating new users
   */
  public User(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * @PrePersist is called automatically before saving a new entity
   *             This ensures timestamps are set even if we forget to set them
   *             manually
   */
  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  /**
   * @PreUpdate is called automatically before updating an existing entity
   *            This ensures updatedAt is always current
   */
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // Getters and Setters
  // JPA and Spring need these to access the private fields

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
