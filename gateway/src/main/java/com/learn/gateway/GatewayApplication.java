package com.learn.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EDUCATIONAL NOTE - API Gateway
 * 
 * An API Gateway is a single entry point for all client requests.
 * Instead of clients calling each microservice directly, they call the gateway
 * which routes requests to the appropriate service.
 * 
 * Benefits:
 * 1. Single endpoint - Clients only need to know one URL
 * 2. Routing - Gateway routes requests based on path
 * 3. Load balancing - Can distribute requests across service instances
 * 4. Authentication - Centralized security (we'll add this later)
 * 5. Rate limiting - Protect backend services from overload
 * 6. Monitoring - Single place to log all requests
 * 
 * Example:
 * Client calls: http://localhost:8080/api/v1/auth/login
 * Gateway routes to: http://auth-service:8081/api/v1/auth/login
 */
@SpringBootApplication
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
