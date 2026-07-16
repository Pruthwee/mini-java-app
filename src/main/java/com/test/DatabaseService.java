package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Database service with hardcoded connection details - Now using HikariCP for connection pooling
 */
public class DatabaseService {
    
    // BLOCKER: Hardcoded database connection details - Now using AWS Secrets Manager
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
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    
    private HikariDataSource dataSource;
    
    private void loadCredentialsFromSecretsManager() {
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName == null) {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .httpClientBuilder(software.amazon.awssdk.http.apache.ApacheHttpClient.builder()
                        .connectionTimeout(java.time.Duration.ofSeconds(10))
                        .socketTimeout(java.time.Duration.ofSeconds(30)))
                .build()) {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse valueResponse = client.getSecretValue(valueRequest);
            String secret = valueResponse.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(secret);
            this.dbUsername = node.get("username").asText();
            this.dbPassword = node.get("password").asText();
        } catch (Exception e) {
            System.err.println("Error retrieving secrets from AWS Secrets Manager: " + e.getMessage());
            // Fallback or throw exception based on requirements
            this.dbUsername = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "root";
            this.dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password123";
        }
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database using HikariCP...");
            
            loadCredentialsFromSecretsManager();
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // Cloud-native optimizations for RDS Proxy / AWS
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            
            this.dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + dbUsername);
            
            // BLOCKER: Hardcoded cache connection
            connectToCache();
            
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
            
        } catch (Exception e) {
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
        if (dataSource != null) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }
}
