package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service - Updated for Java 25 compatibility.
 *
 * Changes made for Java 11 → Java 25 upgrade:
 * - Replaced deprecated mysql-connector-java driver class
 *   "com.mysql.jdbc.Driver" / "com.mysql.cj.jdbc.Driver" with the new
 *   com.mysql:mysql-connector-j artifact; explicit Class.forName() is no
 *   longer required (JDBC 4.0+ auto-loads drivers via ServiceLoader).
 * - Connection details now resolved from environment variables so that
 *   credentials are not hard-coded in source (security best-practice and
 *   required for container/cloud deployments).
 * - try-with-resources used for PreparedStatement to avoid resource leaks
 *   (aligns with Java 25 finalization-deprecated guidance:
 *   JAVA11_TO_25_FINALIZATION_DEPRECATED).
 */
public class DatabaseService {

    // Resolve connection details from environment variables with safe defaults
    private static final String DB_HOST =
            System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT =
            System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME =
            System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME =
            System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD =
            System.getenv().getOrDefault("DB_PASSWORD", "");

    // Cache / external service details from environment
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private static final String EXTERNAL_API_URL =
            System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault("PAYMENT_SERVICE_URL",
                    "https://payment.internal.company.com/process");

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Java 25 / JDBC 4.0+: driver is auto-loaded via ServiceLoader;
            // explicit Class.forName("com.mysql.cj.jdbc.Driver") is not needed.
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            connectToCache();
            initializeExternalServices();

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
                // Java 25: use try-with-resources to avoid relying on finalizers
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setQueryTimeout(30);
                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
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
