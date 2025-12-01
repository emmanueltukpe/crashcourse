package com.learn.authservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * EDUCATIONAL NOTE - JWT (JSON Web Tokens)
 * 
 * JWT is a standard way to securely transmit information between parties as a
 * JSON object.
 * A JWT consists of three parts separated by dots: header.payload.signature
 * 
 * Example JWT:
 * eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
 * 
 * 1. Header (red) - Algorithm and token type
 * 2. Payload (purple) - Claims (data about the user)
 * 3. Signature (blue) - Verifies the token hasn't been tampered with
 * 
 * Why use JWT?
 * - Stateless authentication - Server doesn't need to store sessions
 * - Scalable - Works across multiple servers
 * - Secure - Signed with a secret key
 * 
 * How it works:
 * 1. User logs in with email/password
 * 2. Server verifies credentials and generates JWT
 * 3. Client stores JWT (usually in localStorage or cookie)
 * 4. Client sends JWT in Authorization header for subsequent requests
 * 5. Server validates JWT and extracts user info from it
 */
@Component
public class JwtUtil {

  /**
   * Secret key for signing JWTs
   * 
   * @Value reads from application.yml
   *        IMPORTANT: In production, use a strong, randomly generated secret
   *        and store it in environment variables or a secrets manager, NOT in
   *        code!
   */
  @Value("${jwt.secret}")
  private String secret;

  /**
   * JWT expiration time in milliseconds (default: 24 hours)
   */
  @Value("${jwt.expiration:86400000}")
  private Long expiration;

  /**
   * Generate a JWT token for a user
   * 
   * @param email  User's email
   * @param userId User's ID
   * @return JWT token string
   */
  public String generateToken(String email, Long userId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    claims.put("email", email);

    return createToken(claims, email);
  }

  /**
   * Create a JWT token with given claims and subject
   * 
   * @param claims  Additional data to include in the token
   * @param subject The subject (usually email or username)
   * @return JWT token string
   */
  private String createToken(Map<String, Object> claims, String subject) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expiration);

    // Get the secret key bytes
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

    return Jwts.builder()
        .setClaims(claims) // Set custom claims (userId, email)
        .setSubject(subject) // Set subject (email)
        .setIssuedAt(now) // When the token was created
        .setExpiration(expiryDate) // When the token expires
        .signWith(key, SignatureAlgorithm.HS256) // Sign with secret key
        .compact(); // Build the JWT string
  }

  /**
   * Extract email from JWT token
   * 
   * @param token JWT token
   * @return Email address
   */
  public String extractEmail(String token) {
    return extractClaims(token).getSubject();
  }

  /**
   * Extract user ID from JWT token
   * 
   * @param token JWT token
   * @return User ID
   */
  public Long extractUserId(String token) {
    return extractClaims(token).get("userId", Long.class);
  }

  /**
   * Extract all claims from JWT token
   * 
   * @param token JWT token
   * @return Claims object containing all token data
   */
  private Claims extractClaims(String token) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * Check if a token has expired
   * 
   * @param token JWT token
   * @return true if expired, false otherwise
   */
  public boolean isTokenExpired(String token) {
    return extractClaims(token).getExpiration().before(new Date());
  }

  /**
   * Validate a JWT token
   * 
   * @param token JWT token
   * @param email Expected email
   * @return true if valid, false otherwise
   */
  public boolean validateToken(String token, String email) {
    try {
      String tokenEmail = extractEmail(token);
      return (tokenEmail.equals(email) && !isTokenExpired(token));
    } catch (Exception e) {
      return false;
    }
  }
}
