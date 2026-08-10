package com.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for containerization readiness.
 *
 * <p>Exposes GET /health returning a JSON status response.
 * This endpoint is used by Kubernetes liveness and readiness probes
 * to verify the application is running correctly inside a container.
 *
 * <p>Note: Spring Boot Actuator also provides /actuator/health automatically
 * via the spring-boot-starter-actuator dependency in pom.xml.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * Returns application health status.
     *
     * @return 200 OK with JSON body {"status": "UP", "application": "mini-java-app"}
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "mini-java-app");
        return ResponseEntity.ok(response);
    }
}
