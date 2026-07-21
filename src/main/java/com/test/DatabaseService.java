package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.time.Duration;

public class DatabaseService {
    private static final String DB_HOST = System.getenv("DB_HOST");
    private static final String DB_PORT = System.getenv("DB_PORT");
    private static final String DB_NAME = System.getenv("DB_NAME");
    private static final String DB_URL = "jdbc:mysql://" + (DB_HOST != null ? DB_HOST : "localhost") + ":" + (DB_PORT != null ? DB_PORT : System.getenv("DB_PORT_DEFAULT") != null ? System.getenv("DB_PORT_DEFAULT") : "3306") + "/" + (DB_NAME != null ? DB_NAME : "mini_app_db");
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "127.0.0.1";
    private static final int REDIS_PORT = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "https://api.example.com";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.example.com";

    private HikariDataSource dataSource;
    private String dbUsername;
    private String dbPassword;

    public DatabaseService() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            fetchCredentialsFromSecretsManager();
            initializeConnectionPool();
            connectToCache();
            initializeExternalServices();
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        }
    }

    private void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        
        // Cloud-native optimizations for RDS Proxy / AWS
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(30000);
        config.setPoolName("CloudReadyHikariPool");
        
        // Recommended for MySQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        System.out.println("HikariCP connection pool initialized for " + DB_URL);
    }

    private SecretsManagerClient createSecretsManagerClient() {
        return SecretsManagerClient.builder()
                .overrideConfiguration(conf -> conf.apiCallTimeout(Duration.ofSeconds(10))
                .apiCallAttemptTimeout(Duration.ofSeconds(3)))
                .build();
    }

    private void fetchCredentialsFromSecretsManager() {
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName == null) {
            System.err.println("DB_SECRET_NAME environment variable not set. Falling back to defaults (not recommended).");
            this.dbUsername = "root";
            this.dbPassword = "password123";
            return;
        }
        try (SecretsManagerClient client = createSecretsManagerClient()) {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
            GetSecretValueResponse valueResponse = client.getSecretValue(valueRequest);
            String secret = valueResponse.secretString();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> credentials = mapper.readValue(secret, new TypeReference<Map<String, String>>() {});
            this.dbUsername = credentials.get("username");
            this.dbPassword = credentials.get("password");
        } catch (Exception e) {
            System.err.println("Error fetching secrets: " + e.getMessage());
            this.dbUsername = "root";
            this.dbPassword = "password123";
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at " + REDIS_HOST + ":" + REDIS_PORT);
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            System.out.println("Executing query: " + sql);
            stmt.execute();
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
