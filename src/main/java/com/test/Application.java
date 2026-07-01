package com.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application Entry Point
 * Enables Spring Boot Actuator health check endpoint at /actuator/health
 */
@SpringBootApplication
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("Spring Boot Application started successfully");
        System.out.println("Health check endpoint available at: /actuator/health");
    }
}
