package com.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check controller providing a simple liveness endpoint for container
 * orchestration platforms (Kubernetes liveness / readiness probes).
 *
 * Endpoint: GET /health
 * Response: 200 OK  {"status":"UP","application":"mini-java-app"}
 *
 * Spring Boot Actuator also exposes GET /actuator/health automatically
 * because spring-boot-starter-actuator is on the classpath.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "mini-java-app");
        return ResponseEntity.ok(response);
    }
}
