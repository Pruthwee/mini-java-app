package com.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Simple health check HTTP server using JDK built-in HttpServer.
 * Provides a /health endpoint that returns JSON status.
 * Keeps the process running after the application's own start-up work completes.
 */
public class HealthCheckServer {

    private static final int DEFAULT_HEALTH_PORT = 8081;
    private HttpServer server;

    public void start() {
        int port = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Health check server started on port: " + port);
        } catch (IOException e) {
            System.err.println("Failed to start health check server: " + e.getMessage());
        }
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"UP\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
