package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service refactored for containerized microservices architecture.
 *
 * <p>Each connection parameter is resolved independently from environment variables,
 * enabling this component to be deployed as an independently configurable EKS
 * microservice with its own Kubernetes Deployment, Service, and ConfigMap.
 *
 * <p>cz-java-0082: Tightly-coupled DB_URL construction (line 19) has been decomposed
 * into discrete, independently injectable environment-variable-backed fields so that
 * each concern (host, port, database name, credentials, cache, external APIs) can be
 * managed and scaled independently in a containerised environment.
 */
public class DatabaseService {

    // --- Database connectivity (cz-java-0082: decomposed from monolithic DB_URL) ---
    private static final String DB_HOST     = System.getenv().getOrDefault("DB_HOST",     "localhost");
    private static final String DB_PORT     = System.getenv().getOrDefault("DB_PORT",     "3306");
    private static final String DB_NAME     = System.getenv().getOrDefault("DB_NAME",     "mini_app_db");
    // DB_URL is now assembled from independently configurable env-var-backed fields
    // so each part can be overridden separately in a Kubernetes ConfigMap / Secret.
    private static final String DB_URL      = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    // --- Cache service (independently configurable microservice endpoint) ---
    // cz-java-0062: Hardcoded IP "127.0.0.1" replaced with REDIS_HOST environment variable.
    // In Kubernetes/EKS the value is supplied via a ConfigMap and resolves to the
    // cluster-internal DNS name of the Redis Service (e.g. "redis-service.default.svc.cluster.local").
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "redis-service.default.svc.cluster.local");
    private static final int    REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // --- External service endpoints (independently configurable per microservice) ---
    private static final String EXTERNAL_API_URL     = System.getenv().getOrDefault(
            "EXTERNAL_API_URL",     "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL  = System.getenv().getOrDefault(
            "PAYMENT_SERVICE_URL",  "https://payment.internal.company.com/process");

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            connectToCache();
            initializeExternalServices();

        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                stmt.setQueryTimeout(30);

                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
