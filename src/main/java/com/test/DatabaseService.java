package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;

public class DatabaseService {
    
    // FIX: Replace hard-coded ports and hosts with environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private String dbUsername;
    private String dbPassword;
    
    // FIX: Replace hard-coded API endpoints with environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    private HikariDataSource dataSource;
    
    public void connect() {
        try {
            // FIX: Configure connection timeouts for AWS SDK clients to prevent indefinite hangs
            SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofSeconds(30))
                            .apiCallAttemptTimeout(Duration.ofSeconds(10))
                            .build())
                    .build();

            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId("mini-app/db-credentials")
                    .build();
            String secret = secretsClient.getSecretValue(secretRequest).secretString();
            
            // Assuming secret is stored as a simple comma-separated string "username,password" 
            // or JSON. For simplicity in this fix, we'll assume a basic split or a helper.
            String[] credentials = secret.split(",");
            this.dbUsername = credentials[0];
            this.dbPassword = credentials[1];

            String dbUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/mini_app";
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            
            dataSource = new HikariDataSource(config);
            System.out.println("Database connection pool initialized using username: " + dbUsername);
            
        } catch (Exception e) {
            System.err.println("Database connection pool initialization failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIX: Replace hard-coded Redis connection details with environment variables
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        String redisPort = System.getenv().getOrDefault("REDIS_PORT", "6379");
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // FIX: Use environment variables for external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // FIX: Replace hard-coded query timeout with environment variable
                int timeout = Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT", "30"));
                stmt.setQueryTimeout(timeout);
                
                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
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
