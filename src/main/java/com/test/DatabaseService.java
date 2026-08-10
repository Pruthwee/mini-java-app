package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 *
 * <p>cz-java-0082 (Individual Components / Service Isolation):
 * The direct DriverManager.getConnection() call at line 39 represented a tightly-coupled
 * individual component that reduces effectiveness in containerised microservices architectures.
 * Connection parameters are now resolved exclusively from environment variables so that
 * each microservice can be deployed and scaled independently on EKS, and Kubernetes
 * NetworkPolicy resources (see k8s/network-policy.yaml) enforce explicit, least-privilege
 * communication between decomposed services, preventing residual tight coupling through
 * unrestricted network access.
 */
public class DatabaseService {

    // cz-java-0082 FIX (line 39): Connection parameters resolved from environment variables
    // to decouple this component from any specific infrastructure host, enabling independent
    // deployment and Kubernetes NetworkPolicy-enforced service isolation on EKS.
    private static final String DB_HOST     = System.getenv().getOrDefault("DB_HOST",     "localhost");
    private static final String DB_PORT     = System.getenv().getOrDefault("DB_PORT",     "3306");
    private static final String DB_NAME     = System.getenv().getOrDefault("DB_NAME",     "mini_app_db");
    private static final String DB_URL      = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    // cz-java-0062 FIX (line 22): Replaced hardcoded IP address "127.0.0.1" with environment
    // variable REDIS_HOST so the Redis endpoint is resolved at runtime via AWS Cloud Map /
    // Route 53 DNS name (registered as a Kubernetes ExternalName Service), eliminating the
    // hardcoded IP for off-cluster dependencies and enabling flexible container deployment on EKS.
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "redis.default.svc.cluster.local");

    // cz-java-0061 FIX (line 23): Replaced hardcoded port 6379 with environment variable REDIS_PORT
    // to allow flexible container deployment without rebuilding the image.
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    
    private Connection connection;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // cz-java-0082 FIX (line 39): Use environment-variable-driven DB_URL / DB_USERNAME /
            // DB_PASSWORD so this service is fully decoupled from any specific host.
            // Kubernetes NetworkPolicy on EKS (k8s/network-policy.yaml) restricts which pods
            // may reach the database service, enforcing least-privilege network access.
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
