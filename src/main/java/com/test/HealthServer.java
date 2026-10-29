package com.test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Simple health check endpoint: GET /health answers {"status":"UP"}.
 * It runs on its own port (HEALTH_PORT, 8081 by default), apart from the
 * application's server socket, and keeps the process running after the
 * application's own work ends.
 */
public class HealthServer {

    private static final byte[] BODY = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);

    public static HttpServer start() throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, BODY.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(BODY);
            }
        });
        // a non-daemon executor thread keeps the JVM alive after main returns
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        System.out.println("Health endpoint started on port: " + port + " (GET /health)");
        return server;
    }
}
