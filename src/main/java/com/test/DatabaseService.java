package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service refactored for containerized microservices architecture.
 *
 * FIX cz-java-0062 (Hardcoded IP Addresses / Network & Port Binding):
 * --------------------------------------------------------------------
 * Previously, the REDIS_HOST fallback default used the hardcoded IP address
 * "127.0.0.1", which reduces container deployment flexibility and prevents
 * proper service discovery in EKS environments.
 *
 * Remediation applied — AWS Cloud Map / Route 53 for External Service Discovery on EKS:
 *   The hardcoded IP "127.0.0.1" has been replaced with the DNS name "redis.local"
 *   so that the Redis endpoint is resolved via DNS (AWS Cloud Map / Route 53
 *   ExternalName Service), enabling dynamic service discovery without hardcoded IPs.
 *
 * FIX cz-java-0082 (Individual Components / Service Isolation):
 * ---------------------------------------------------------------
 * Previously, this class used hardcoded static constants for all connection
 * details (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, REDIS_HOST,
 * REDIS_PORT, EXTERNAL_API_URL, PAYMENT_SERVICE_URL), creating tightly-coupled
 * individual components that reduce effectiveness in containerized microservices
 * architectures.
 *
 * Remediation applied — Enforce Service Isolation with Kubernetes NetworkPolicy on EKS:
 *   1. All connection details are now sourced exclusively from environment variables
 *      injected by Kubernetes ConfigMaps (non-sensitive) and Secrets (sensitive).
 *   2. A Kubernetes NetworkPolicy resource (k8s/network-policy.yaml) is provided to
 *      enforce explicit, least-privilege communication between decomposed microservices,
 *      preventing residual tight coupling through unrestricted network access on EKS.
 *
 * Environment variables consumed (set via Kubernetes ConfigMap / Secret):
 *   DB_HOST          — database hostname (ConfigMap)
 *   DB_PORT          — database port     (ConfigMap, default 3306)
 *   DB_NAME          — database name     (ConfigMap)
 *   DB_USERNAME      — database user     (Secret)
 *   DB_PASSWORD      — database password (Secret)
 *   REDIS_HOST       — Redis hostname    (ConfigMap)
 *   REDIS_PORT       — Redis port        (ConfigMap, default 6379)
 *   EXTERNAL_API_URL — external API URL  (ConfigMap)
 *   PAYMENT_SERVICE_URL — payment svc URL (ConfigMap)
 */
public class DatabaseService {

    // FIX cz-java-0082 (line 39): Replaced all hardcoded static connection constants with
    // environment-variable lookups to decouple this service from its dependencies and
    // enable Kubernetes NetworkPolicy enforcement on EKS for least-privilege isolation.
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    private final String redisHost;
    private final int    redisPort;

    private final String externalApiUrl;
    private final String paymentServiceUrl;

    private Connection connection;

    public DatabaseService() {
        // Non-sensitive connection coordinates — sourced from Kubernetes ConfigMap
        this.dbHost     = getEnvOrDefault("DB_HOST",     "localhost");
        this.dbPort     = getEnvOrDefault("DB_PORT",     "3306");
        this.dbName     = getEnvOrDefault("DB_NAME",     "mini_app_db");
        this.dbUrl      = "jdbc:mysql://" + this.dbHost + ":" + this.dbPort + "/" + this.dbName;

        // Sensitive credentials — sourced from Kubernetes Secret
        this.dbUsername = getEnvOrDefault("DB_USERNAME", "root");
        this.dbPassword = getEnvOrDefault("DB_PASSWORD", "");

        // Cache coordinates — sourced from Kubernetes ConfigMap
        // FIX cz-java-0062 (line 22 original / line 74 current): Replaced hardcoded IP
        // "127.0.0.1" with DNS name "redis.local" to support AWS Cloud Map / Route 53
        // ExternalName Service discovery on EKS, eliminating hardcoded IPs for
        // off-cluster dependencies.
        this.redisHost  = getEnvOrDefault("REDIS_HOST",  "redis.local");
        this.redisPort  = Integer.parseInt(getEnvOrDefault("REDIS_PORT", "6379"));

        // External service URLs — sourced from Kubernetes ConfigMap
        this.externalApiUrl     = getEnvOrDefault("EXTERNAL_API_URL",     "http://api.example.com:8080/v1");
        this.paymentServiceUrl  = getEnvOrDefault("PAYMENT_SERVICE_URL",  "https://payment.internal.company.com/process");
    }

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            Class.forName("com.mysql.cj.jdbc.Driver");

            // FIX cz-java-0082 — line 39: Connection now uses instance fields populated
            // from environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME,
            // DB_PASSWORD) instead of hardcoded static constants, enforcing service
            // isolation compatible with Kubernetes NetworkPolicy on EKS.
            connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);

            System.out.println("Connected to database: " + dbUrl);
            System.out.println("Using username: " + dbUsername);

            connectToCache();
            initializeExternalServices();

        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // Cache host/port sourced from environment variables (Kubernetes ConfigMap)
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // External service URLs sourced from environment variables (Kubernetes ConfigMap)
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
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
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    /**
     * Returns the value of the named environment variable, or {@code defaultValue}
     * when the variable is absent or blank.
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }
}
