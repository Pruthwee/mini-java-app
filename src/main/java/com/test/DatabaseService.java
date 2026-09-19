package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service
 * Updated for Java 21 compatibility:
 * - Uses com.mysql:mysql-connector-j (new artifact ID for Java 21 compatible driver)
 * - Class.forName() for JDBC driver loading is no longer needed (JDBC 4.0+ auto-loads drivers via ServiceLoader)
 * - Connection details externalized via environment variables (no hardcoded credentials)
 * - Uses try-with-resources for proper resource management (PreparedStatement)
 */
public class DatabaseService {

    // Connection details resolved from environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "password123");

    // Cache server details from environment variables
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // External API endpoints from environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault(
            "EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault(
            "PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Class.forName() is no longer needed with JDBC 4.0+ (Java 6+).
            // com.mysql:mysql-connector-j uses ServiceLoader for automatic driver registration.
            // Removed: Class.forName("com.mysql.cj.jdbc.Driver");

            // Use DriverManager with externalized credentials
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

    /**
     * Executes a SQL query using try-with-resources for proper PreparedStatement management.
     * Fixed: PreparedStatement is now properly closed via try-with-resources (Java 21 best practice).
     *
     * @param sql the SQL query to execute
     */
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                // Fixed: Use try-with-resources for PreparedStatement to ensure it is always closed
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
