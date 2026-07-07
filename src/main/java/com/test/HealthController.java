package com.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for containerized deployment readiness and liveness probes.
 * Exposes GET /health returning HTTP 200 with JSON status when the application is running.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * Liveness / readiness health check endpoint.
     * Returns HTTP 200 OK with a JSON body indicating the application status.
     *
     * @return ResponseEntity with status "UP"
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("application", "mini-java-app");
        return ResponseEntity.ok(status);
    }
}
