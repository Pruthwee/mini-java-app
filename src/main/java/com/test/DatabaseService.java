package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service migrated from MySQL to PostgreSQL 16.
 *
 * Migration changes applied:
 * - DB_URL: Updated JDBC URL scheme from jdbc:mysql:// to jdbc:postgresql://
 * - DB_PORT: Updated default port from 3306 (MySQL) to 5432 (PostgreSQL)
 * - DB_USERNAME: Updated default username from root to postgres
 * - DRIVER_CLASS: Updated from com.mysql.cj.jdbc.Driver to org.postgresql.Driver
 * - JDBC driver dependency updated in pom.xml from mysql-connector-j to postgresql 42.7.3
 */
public class DatabaseService {

    // Updated database connection details for PostgreSQL 16
    private static final String DB_HOST = "localhost";
    // Updated port from 3306 (MySQL) to 5432 (PostgreSQL default port)
    private static final String DB_PORT = "5432";
    private static final String DB_NAME = "mini_app_db";
    // Updated JDBC URL scheme from jdbc:mysql:// to jdbc:postgresql://
    private static final String DB_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    // Updated username from root (MySQL default) to postgres (PostgreSQL default)
    private static final String DB_USERNAME = "postgres";
    private static final String DB_PASSWORD = "password123";

    // Cache server details
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;

    // External API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to PostgreSQL database...");

            // Updated JDBC driver class from com.mysql.cj.jdbc.Driver to org.postgresql.Driver
            Class.forName("org.postgresql.Driver");

            // Connect using updated PostgreSQL JDBC URL and credentials
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to PostgreSQL database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            // Cache connection
            connectToCache();

            // External service initialization
            initializeExternalServices();

        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("PostgreSQL database connection failed: " + e.getMessage());
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
                System.out.println("PostgreSQL database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
