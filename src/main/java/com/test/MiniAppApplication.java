package com.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Mini Java Application.
 * Enables Spring Boot auto-configuration including Actuator health endpoint
 * at /actuator/health (provided by spring-boot-starter-actuator).
 */
@SpringBootApplication
public class MiniAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAppApplication.class, args);
    }
}
