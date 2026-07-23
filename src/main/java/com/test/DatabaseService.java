package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // Use environment variables for configuration
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Secret name for AWS Secrets Manager
    private static final String SECRET_NAME = System.getenv("DB_SECRET_NAME") != null ? System.getenv("DB_SECRET_NAME") : "mini-app/db-credentials";
    
    // REMEDIATION: Replace hard-coded ports with AWS Parameter Store and environment variable injection
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "127.0.0.1";
    private static final int REDIS_PORT = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
    
    // REMEDIATION: Replace hard-coded ports with AWS Parameter Store and environment variable injection
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
    
        // REMEDIATION: Configure connection timeouts for AWS SDK clients
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(30))
                .apiCallAttemptTimeout(Duration.ofSeconds(30))
                .build();

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .overrideConfiguration(overrideConfig)
                .build()) {
            GetSecretValueResponse valueResponse = client.getSecretValue(valueRequest);
            String secret = valueResponse.secretString();
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(secret);
            return node.get(key).asText();
        } catch (Exception e) {
            System.err.println("Error retrieving secret " + key + " from AWS Secrets Manager: " + e.getMessage());
            return null;
        }
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // Retrieve credentials from AWS Secrets Manager
            String username = getSecret("username");
            String password = getSecret("password");
            
            if (username == null || password == null) {
                throw new SQLException("Could not retrieve database credentials from AWS Secrets Manager");
            }
            
            // REMEDIATION: Replace raw JDBC with HikariCP connection pool and RDS Proxy
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // Cloud-native optimizations for RDS Proxy / HikariCP
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database using HikariCP: " + DB_URL);
            System.out.println("Using username: " + username);
            
            // BLOCKER: Hardcoded cache connection
            connectToCache();
            
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
            
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
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // BLOCKER: Hardcoded query timeout
                    stmt.setQueryTimeout(30);
                    
                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }
}
