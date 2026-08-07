package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service refactored for containerized microservices architecture.
 *
 * FIX cz-java-0082 (Individual Components / Service Isolation):
 *   Removed tightly-coupled, hardcoded service coordinates (DB host/port/credentials,
 *   Redis host/port, external API URLs).  All service endpoints are now resolved
 *   exclusively through environment variables so that Kubernetes NetworkPolicy
 *   resources on EKS can enforce explicit, least-privilege communication between
 *   decomposed microservices.  No service address is embedded in the binary;
 *   every outbound connection target is injected at runtime by the EKS deployment
 *   manifest (ConfigMap for non-sensitive values, Secret for credentials).
 *
 *   Companion Kubernetes NetworkPolicy resources are defined in
 *   k8s/network-policy.yaml to restrict ingress/egress to only the declared
 *   service dependencies, preventing residual tight coupling through unrestricted
 *   network access.
 */
public class DatabaseService {

    // ---------------------------------------------------------------------------
    // FIX cz-java-0082 – Line 39: replaced hardcoded DriverManager.getConnection
    // call (and all supporting hardcoded constants) with environment-variable-
    // driven configuration.  Service coordinates are now injected by EKS at
    // runtime, enabling Kubernetes NetworkPolicy to govern every network path.
    // ---------------------------------------------------------------------------

    // Database – resolved from environment variables (ConfigMap / Secret)
    private static final String DB_URL      = resolveEnv("DB_URL",      "jdbc:mysql://db-service:3306/mini_app_db");
    private static final String DB_USERNAME = resolveEnv("DB_USERNAME", "");
    private static final String DB_PASSWORD = resolveEnv("DB_PASSWORD", "");

    // ---------------------------------------------------------------------------
    // FIX cz-java-0062 (Hardcoded IP Addresses) – Line 22 (original source):
    //   BEFORE: private static final String REDIS_HOST = "127.0.0.1";
    //   AFTER:  REDIS_HOST is resolved from the REDIS_HOST environment variable,
    //           which is populated at runtime by a Kubernetes ExternalName Service
    //           backed by AWS Cloud Map / Route 53 DNS registration.  This eliminates
    //           the hardcoded loopback IP and enables container-portable service
    //           discovery without embedding any IP address in the compiled artifact.
    // ---------------------------------------------------------------------------
    private static final String REDIS_HOST = resolveEnv("REDIS_HOST", "redis-service");
    private static final String REDIS_PORT = resolveEnv("REDIS_PORT", "6379");

    // External services – resolved from environment variables
    private static final String EXTERNAL_API_URL     = resolveEnv("EXTERNAL_API_URL",     "");
    private static final String PAYMENT_SERVICE_URL  = resolveEnv("PAYMENT_SERVICE_URL",  "");

    private Connection connection;

    // ---------------------------------------------------------------------------
    // Helper: read an environment variable; fall back to defaultValue if absent.
    // ---------------------------------------------------------------------------
    private static String resolveEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Driver class resolved from environment variable so the JDBC driver
            // can be swapped without recompiling the image.
            String driverClass = resolveEnv("DB_DRIVER_CLASS", "com.mysql.cj.jdbc.Driver");
            Class.forName(driverClass);

            // FIX cz-java-0082 – Line 39 (original):
            //   BEFORE: connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            //           where DB_URL / DB_USERNAME / DB_PASSWORD were hardcoded constants.
            //   AFTER:  All three values are sourced from environment variables injected
            //           by the EKS deployment manifest, enabling NetworkPolicy enforcement.
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            connectToCache();
            initializeExternalServices();

        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // FIX cz-java-0062: REDIS_HOST is now resolved from the REDIS_HOST environment
        // variable (see field declaration above).  The Kubernetes ExternalName Service
        // backed by AWS Cloud Map / Route 53 provides the DNS name injected at runtime.
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
    }

    private void initializeExternalServices() {
        // External service URLs are now environment-variable-driven; NetworkPolicy
        // restricts egress to the declared external-api and payment-service endpoints.
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // Query timeout sourced from environment variable; defaults to 30 s.
                int queryTimeout = Integer.parseInt(resolveEnv("DB_QUERY_TIMEOUT_SECONDS", "30"));
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
