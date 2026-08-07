package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 *
 * FIX cz-java-0082 (Lines 39-39): Enforced service isolation by externalizing all database
 * connection parameters to environment variables. This enables Kubernetes NetworkPolicy on EKS
 * to enforce explicit, least-privilege communication between decomposed microservices, preventing
 * residual tight coupling through unrestricted network access. Each microservice now resolves its
 * own peer endpoints at runtime via environment variables, making NetworkPolicy enforcement
 * effective and auditable.
 */
public class DatabaseService {
    
    // FIX cz-java-0082: Externalized database connection details to environment variables.
    // Kubernetes NetworkPolicy on EKS enforces service-to-service isolation; hardcoded
    // hostnames/credentials bypass that isolation by embedding peer addresses in code.
    // Resolving these at runtime from env vars allows NetworkPolicy rules to be the
    // single authoritative control plane for inter-service communication.
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
    private static final String DB_URL  = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";

    // FIX cz-java-0062 (Line 22): Replaced hardcoded IP address "127.0.0.1" with environment
    // variable REDIS_HOST. Register the Redis/cache service in AWS Cloud Map or Route 53 and
    // reference it by DNS name via a Kubernetes ExternalName Service. The DNS name is injected
    // at runtime via the REDIS_HOST environment variable, eliminating the hardcoded IP and
    // enabling container-portable deployments on EKS.
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null
            ? System.getenv("REDIS_HOST")
            : "localhost";

    // FIX cz-java-0061 (Line 23): Externalized REDIS_PORT to environment variable for Helm chart
    // parameterization. The port value is supplied via Helm values.yaml (service.redisPort) and
    // injected as REDIS_PORT env var, enabling consistent port references across Deployment,
    // Service, Ingress, and health probe resources on EKS.
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv("REDIS_PORT") != null ? System.getenv("REDIS_PORT") : "6379");
    
    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    
    private Connection connection;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // FIX cz-java-0082 (Line 39): Connection now uses environment-variable-resolved
            // DB_URL, DB_USERNAME, and DB_PASSWORD. This decouples the service from any
            // specific database instance and allows Kubernetes NetworkPolicy on EKS to
            // govern which pods may reach the database service endpoint, enforcing
            // least-privilege network access between decomposed microservices.
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
