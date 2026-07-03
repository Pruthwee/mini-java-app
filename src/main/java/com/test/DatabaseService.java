package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import java.time.Duration;

public class DatabaseService {
    
    // BLOCKER: Hardcoded database connection details
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private String dbUsername;
    private String dbPassword;
    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
    
    private HikariDataSource dataSource;
    
    public void connect() {
        try {
            loadSecrets();
            String dbUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/mydb";
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // Cloud-native optimizations for RDS Proxy / HikariCP
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            config.setPoolName("HikariPool-DatabaseService");
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Using username: " + dbUsername);
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection pool initialization failed: " + e.getMessage());
        }
    }

    private void loadSecrets() {
        try {
            // Fix for cr-java-0097: Configure connection timeouts for AWS SDK clients
            ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
            httpClientBuilder.connectionTimeout(Duration.ofSeconds(10));
            httpClientBuilder.socketTimeout(Duration.ofSeconds(10));

            SecretsManagerClient client = SecretsManagerClient.builder()
                .httpClient(httpClientBuilder.build())
                .build();
                
            String secretName = System.getenv("DB_SECRET_NAME");
            if (secretName == null) {
                throw new RuntimeException("Environment variable DB_SECRET_NAME is not set");
            }
            String secretValue = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build()).secretString();
            // Assuming secret is stored as a simple comma-separated string "username,password" or JSON
            String[] parts = secretValue.split(",");
            this.dbUsername = parts[0];
            this.dbPassword = parts[1];
        } catch (Exception e) {
            System.err.println("Failed to load secrets from AWS Secrets Manager: " + e.getMessage());
        }
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
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                System.out.println("Database connection pool closed");
            }
        } catch (Exception e) {
            System.err.println("Failed to close database connection pool: " + e.getMessage());
        }
    }
}
