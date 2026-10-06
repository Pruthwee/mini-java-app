package com.test;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 *
 * Java 21 upgrade changes:
 * - Replaced deprecated Thread.getId() with Thread.threadId()
 *   (Rule: JAVA11_TO_21_THREAD_GETID_DEPRECATED – Thread.getId() is deprecated in Java 19+;
 *   threadId() is the correct replacement.)
 * - Replaced new URL(String) constructor with URI.create(...).toURL()
 *   (Rule: JAVA11_TO_21_URL_CONSTRUCTOR_DEPRECATED – URL(String) constructors are deprecated
 *   in Java 20+; construct a URI and convert to URL at the boundary that still needs one.)
 */
public class DatabaseService {
    
    // BLOCKER: Hardcoded database connection details
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "password123";
    
    // BLOCKER: Hardcoded cache server details
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    
    // BLOCKER: Hardcoded API endpoints
    // Java 21 upgrade: store as URI strings; convert to URL only when a URL is strictly required
    // (JAVA11_TO_21_URL_CONSTRUCTOR_DEPRECATED)
    private static final String EXTERNAL_API_URI_STR = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URI_STR = "https://payment.internal.company.com/process";
    
    private Connection connection;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Java 21 upgrade: log current thread id using threadId() instead of deprecated getId()
            // (JAVA11_TO_21_THREAD_GETID_DEPRECATED)
            long tid = Thread.currentThread().threadId();
            System.out.println("Connecting on thread id: " + tid);
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // BLOCKER: Hardcoded connection string and credentials
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);
            
            // BLOCKER: Hardcoded cache connection
            connectToCache();
            
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
            
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // BLOCKER: Hardcoded Redis connection details
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // Java 21 upgrade: use URI.create() instead of new URL(String) to avoid deprecated constructor
        // (JAVA11_TO_21_URL_CONSTRUCTOR_DEPRECATED)
        URI externalApiUri = URI.create(EXTERNAL_API_URI_STR);
        URI paymentServiceUri = URI.create(PAYMENT_SERVICE_URI_STR);

        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + externalApiUri);
        System.out.println("Initializing payment service: " + paymentServiceUri);
    }
    
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // BLOCKER: Hardcoded query timeout
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
