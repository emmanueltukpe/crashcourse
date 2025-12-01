package com.learn.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EDUCATIONAL NOTE - @SpringBootApplication
 * 
 * This is the main entry point for the Spring Boot application.
 * 
 * @SpringBootApplication is a convenience annotation that combines three
 *                        important annotations:
 * 
 *                        1. @Configuration - Marks this class as a source of
 *                        bean definitions
 *                        2. @EnableAutoConfiguration - Tells Spring Boot to
 *                        automatically configure your application
 *                        based on the dependencies you've added (e.g., seeing
 *                        JPA dependency → configure JPA)
 *                        3. @ComponentScan - Tells Spring to scan this package
 *                        and sub-packages for components
 *                        (classes annotated
 *                        with @Component, @Service, @Repository, @Controller,
 *                        etc.)
 * 
 *                        The main method calls SpringApplication.run() which:
 *                        - Starts the Spring application context
 *                        - Starts the embedded Tomcat server
 *                        - Initializes all beans (objects managed by Spring)
 *                        - Makes your REST APIs available
 */
@SpringBootApplication
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
