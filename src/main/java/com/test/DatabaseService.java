package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // Use environment variables for database connection details
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "password123");
    
    // Use environment variables for cache server details
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // Use environment variables for API endpoints
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");
            // In a real app, we would use DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);
            initializeExternalServices();
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
        System.out.println("Connecting to Redis at " + REDIS_HOST + ":" + REDIS_PORT);
    }

    public void executeQuery(String sql) {
        try {
            if (connection == null) {
                System.out.println("No connection available, simulating query execution...");
            }
            
            // Simulate PreparedStatement
            int timeout = Integer.parseInt(System.getenv().getOrDefault("DB_QUERY_TIMEOUT", "30"));
            System.out.println("Executing query with timeout " + timeout + "s: " + sql);
            
        } catch (Exception e) {
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