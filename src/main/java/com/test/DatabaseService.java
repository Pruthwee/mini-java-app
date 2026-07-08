package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // BLOCKER: Hardcoded database connection details
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Credentials will be fetched from AWS Secrets Manager
    private String dbUsername;
    private String dbPassword;
    
    // BLOCKER: Hardcoded cache server details
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "127.0.0.1";
    private static final int REDIS_PORT = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
    
    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
    
    private Connection connection;
    
    private void fetchCredentialsFromSecretsManager() {
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName == null) {
            this.dbUsername = node.get("username").asText();
            this.dbPassword = node.get("password").asText();
        } catch (Exception e) {
            System.err.println("Error fetching secrets from AWS Secrets Manager: " + e.getMessage());
            // Fallback or throw exception based on requirements
            this.dbUsername = "root";
            this.dbPassword = "password123";
        }
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            fetchCredentialsFromSecretsManager();
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // BLOCKER: Hardcoded connection string and credentials
            connection = DriverManager.getConnection(DB_URL, dbUsername, dbPassword);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + dbUsername);
            
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
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
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