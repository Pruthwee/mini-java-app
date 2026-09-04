package com.test;

import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

/**
 * Database service updated for Azure cloud readiness.
 */
public class DatabaseService {

    private static final String DEFAULT_DB_HOST = "localhost";
    private static final String DEFAULT_DB_PORT = "3306";
    private static final String DEFAULT_DB_NAME = "mini_app_db";
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final String DEFAULT_REDIS_PORT = "6379";
    private static final String DEFAULT_EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String DEFAULT_PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10000;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 30000;

    private final HikariDataSource dataSource;
    private final HttpClient httpClient;

    public DatabaseService() {
        this.dataSource = createDataSource();
        this.httpClient = new NettyAsyncHttpClientBuilder()
                .connectTimeout(Duration.ofMillis(resolveInt("HTTP_CONNECT_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS)))
                .readTimeout(Duration.ofMillis(resolveInt("HTTP_READ_TIMEOUT_MS", DEFAULT_SOCKET_TIMEOUT_MS)))
                .writeTimeout(Duration.ofMillis(resolveInt("HTTP_WRITE_TIMEOUT_MS", DEFAULT_SOCKET_TIMEOUT_MS)))
                .build();
    }

    public void connect() {
        try (Connection connection = dataSource.getConnection()) {
            System.out.println("Connecting to database...");
            System.out.println("Connected to database: " + buildJdbcUrl());
            System.out.println("Using username from Azure Key Vault secret: " + resolveSecretName("DB_USERNAME_SECRET_NAME", "db-username"));
            connectToCache();
            initializeExternalServices();
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        String redisHost = resolveConfig("REDIS_HOST", DEFAULT_REDIS_HOST);
        String redisPort = resolveConfig("REDIS_PORT", DEFAULT_REDIS_PORT);
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }

    private void initializeExternalServices() {
        String externalApiUrl = resolveConfig("EXTERNAL_API_URL", DEFAULT_EXTERNAL_API_URL);
        String paymentServiceUrl = resolveConfig("PAYMENT_SERVICE_URL", DEFAULT_PAYMENT_SERVICE_URL);
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
        System.out.println("Azure HTTP client configured: " + (httpClient != null));
    }

    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setQueryTimeout(resolveInt("DB_QUERY_TIMEOUT_SECONDS", DEFAULT_QUERY_TIMEOUT_SECONDS));
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

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl());
        config.setUsername(resolveSecret("DB_USERNAME_SECRET_NAME", "db-username"));
        config.setPassword(resolveSecret("DB_PASSWORD_SECRET_NAME", "db-password"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(resolveInt("DB_MAX_POOL_SIZE", 10));
        config.setMinimumIdle(resolveInt("DB_MIN_IDLE", 2));
        config.setConnectionTimeout(resolveInt("DB_CONNECTION_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS));
        config.setValidationTimeout(resolveInt("DB_VALIDATION_TIMEOUT_MS", 5000));
        config.setIdleTimeout(resolveInt("DB_IDLE_TIMEOUT_MS", 600000));
        config.setMaxLifetime(resolveInt("DB_MAX_LIFETIME_MS", 1800000));
        config.addDataSourceProperty("connectTimeout", resolveInt("DB_SOCKET_CONNECT_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS));
        config.addDataSourceProperty("socketTimeout", resolveInt("DB_SOCKET_TIMEOUT_MS", DEFAULT_SOCKET_TIMEOUT_MS));
        config.addDataSourceProperty("loginTimeout", resolveInt("DB_LOGIN_TIMEOUT_SECONDS", 10));
        config.addDataSourceProperty("tcpKeepAlive", true);
        return new HikariDataSource(config);
    }

    private String buildJdbcUrl() {
        String dbHost = resolveConfig("DB_HOST", DEFAULT_DB_HOST);
        String dbPort = resolveConfig("DB_PORT", DEFAULT_DB_PORT);
        String dbName = resolveConfig("DB_NAME", DEFAULT_DB_NAME);
        return String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=true&requireSSL=false&serverTimezone=UTC&connectTimeout=%d&socketTimeout=%d",
                dbHost,
                dbPort,
                dbName,
                resolveInt("DB_SOCKET_CONNECT_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS),
                resolveInt("DB_SOCKET_TIMEOUT_MS", DEFAULT_SOCKET_TIMEOUT_MS));
    }

    private String resolveConfig(String envKey, String defaultValue) {
        return Optional.ofNullable(System.getenv(envKey))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private int resolveInt(String envKey, int defaultValue) {
        try {
            return Integer.parseInt(resolveConfig(envKey, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String resolveSecret(String envSecretNameKey, String defaultSecretName) {
        String secretName = resolveSecretName(envSecretNameKey, defaultSecretName);
        String keyVaultUrl = System.getenv("AZURE_KEY_VAULT_URL");
        if (keyVaultUrl == null || keyVaultUrl.isBlank()) {
            throw new IllegalStateException("Missing environment variable: AZURE_KEY_VAULT_URL");
        }
        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        return secretClient.getSecret(secretName).getValue();
    }

    private String resolveSecretName(String envSecretNameKey, String defaultSecretName) {
        return resolveConfig(envSecretNameKey, defaultSecretName);
    }
}