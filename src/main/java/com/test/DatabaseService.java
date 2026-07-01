package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with externalized configuration for containerization
 */
public class DatabaseService {
    
    // FIXED: Externalized database connection details using environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "password123");
    
    // FIXED: Externalized cache server details using environment variables and DNS-based discovery
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "redis-service");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // FIXED: Externalized API endpoints using environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    private Connection connection;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // Load JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // FIXED: Using externalized connection string and credentials from environment variables
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);
            
            // Connect to cache with externalized configuration
            connectToCache();
            
            // Initialize external services with externalized URLs
            initializeExternalServices();
            
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIXED: Using DNS-based service discovery and externalized configuration
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // FIXED: Using externalized service URLs from environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // Query timeout can be externalized if needed
                int queryTimeout = Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT", "30"));
                stmt.setQueryTimeout(queryTimeout);
                
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
