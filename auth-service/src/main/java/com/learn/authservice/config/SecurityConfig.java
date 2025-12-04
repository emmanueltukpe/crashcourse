package com.learn.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * EDUCATIONAL NOTE - Spring Security Configuration
 * 
 * This class configures Spring Security for the auth-service.
 * 
 * For Stage 1, we're keeping security simple:
 * - All auth endpoints (/api/v1/auth/**) are public
 * - No session management (stateless for JWT)
 * - CSRF disabled (common for REST APIs using JWT)
 * 
 * In later stages, we'll add:
 * - JWT token validation
 * - Protected endpoints
 * - Role-based access control
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST APIs (we use JWT tokens instead)
            .csrf(csrf -> csrf.disable())
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Allow all requests to auth endpoints (register, login, health)
                .requestMatchers("/api/v1/auth/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Use stateless session management (no server-side sessions)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        
        return http.build();
    }
}

