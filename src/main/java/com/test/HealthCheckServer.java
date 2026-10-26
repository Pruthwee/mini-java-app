package com.test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Simple health check HTTP endpoint for container orchestration.
 * Serves GET /health on a dedicated port (default 8081) so that
 * Kubernetes liveness and readiness probes can verify the process is alive.
 */
public class HealthCheckServer {

    private static final int HEALTH_PORT = Integer.parseInt(
        System.getenv().getOrDefault("HEALTH_PORT", "8081"));

    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(HEALTH_PORT), 0);
            server.createContext("/health", new HealthHandler());
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            System.out.println("Health check endpoint started on port " + HEALTH_PORT);
        } catch (IOException e) {
            System.err.println("Failed to start health check server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"UP\"}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
