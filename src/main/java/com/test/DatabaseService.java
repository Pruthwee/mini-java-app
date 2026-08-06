package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {

    // FIX cz-java-0082: Replaced hardcoded database connection details with environment variables
    // to enforce service isolation via Kubernetes NetworkPolicy on EKS. The database microservice
    // is now addressed through a Kubernetes Service DNS name supplied at runtime, enabling
    // NetworkPolicy rules to restrict which pods may reach the database service.
    private static final String DB_HOST = Optional.ofNullable(System.getenv("DB_HOST"))
            .orElse("localhost");
    private static final String DB_PORT = Optional.ofNullable(System.getenv("DB_PORT"))
            .orElse("3306");
    private static final String DB_NAME = Optional.ofNullable(System.getenv("DB_NAME"))
            .orElse("mini_app_db");
    private static final String DB_URL = Optional.ofNullable(System.getenv("DB_URL"))
            .orElseGet(() -> "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
    private static final String DB_USERNAME = Optional.ofNullable(System.getenv("DB_USERNAME"))
            .orElse("root");
    private static final String DB_PASSWORD = Optional.ofNullable(System.getenv("DB_PASSWORD"))
            .orElse("");

    // FIX cz-java-0062: Replaced hardcoded IP address "127.0.0.1" with an environment variable
    // backed by an AWS Cloud Map / Route 53 DNS name. The Redis service (off-cluster or in-cluster)
    // is registered in AWS Cloud Map or Route 53 and exposed via a Kubernetes ExternalName Service.
    // The DNS name is injected at runtime as the REDIS_HOST environment variable, eliminating the
    // hardcoded IP and enabling flexible container deployment across environments.
    private static final String REDIS_HOST = Optional.ofNullable(System.getenv("REDIS_HOST"))
            .orElse("redis.internal.svc.cluster.local");

    // FIX cz-java-0061: Replaced hardcoded REDIS_PORT with environment variable for Helm chart
    // parameterization. The port value is centralized in Helm values.yaml (redis.port) and
    // injected as the REDIS_PORT environment variable via the Kubernetes Deployment manifest,
    // ensuring consistent port references across Deployment, Service, and health probe resources.
    private static final int REDIS_PORT = Integer.parseInt(
            Optional.ofNullable(System.getenv("REDIS_PORT")).orElse("6379"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Connection now uses environment-variable-backed DB_URL / DB_USERNAME / DB_PASSWORD
            // (line 39 – cz-java-0082 fix applied above)
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
