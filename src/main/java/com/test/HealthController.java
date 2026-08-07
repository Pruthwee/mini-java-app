package com.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for container liveness and readiness probes.
 *
 * health-check-endpoint:
 *   Exposes GET /health returning HTTP 200 with a JSON status payload.
 *   This endpoint is used by Kubernetes liveness and readiness probes
 *   defined in the EKS Deployment manifest.
 *
 *   Spring Boot Actuator also exposes /actuator/health (configured in
 *   application.properties) for richer health detail when needed.
 */
@RestController
public class HealthController {

    /**
     * Simple liveness/readiness probe endpoint.
     *
     * @return HTTP 200 with JSON body: {"status":"UP","service":"mini-java-app"}
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "mini-java-app");
        return ResponseEntity.ok(response);
    }
}
