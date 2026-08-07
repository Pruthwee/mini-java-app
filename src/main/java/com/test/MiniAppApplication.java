package com.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Mini Java Application.
 *
 * health-check-endpoint: Spring Boot Actuator is on the classpath
 * (spring-boot-starter-actuator in pom.xml).  The /actuator/health endpoint
 * is automatically exposed via the management.endpoints.web.exposure.include=health
 * property in application.properties.  No additional code is required; the
 * HealthController class provides an additional lightweight /health alias for
 * liveness/readiness probes that do not use the full Actuator path.
 */
@SpringBootApplication
public class MiniAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAppApplication.class, args);
    }
}
