package com.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Mini Java Application.
 *
 * Health check endpoint is automatically provided by Spring Boot Actuator at:
 *   GET /actuator/health  → {"status":"UP"}
 *
 * The actuator dependency (spring-boot-starter-actuator) is already declared
 * in pom.xml. No additional configuration is required for the default health
 * endpoint to be available.
 */
@SpringBootApplication
public class MiniAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAppApplication.class, args);
    }
}
