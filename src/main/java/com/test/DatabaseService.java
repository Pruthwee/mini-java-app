package com.test;

import com.azure.core.credential.TokenCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service updated for Azure cloud readiness.
 */
public class DatabaseService {

    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    private final AzureConfigurationHelper configurationHelper = new AzureConfigurationHelper();
    private final AzureKeyVaultHelper keyVaultHelper = new AzureKeyVaultHelper(configurationHelper);
    private HikariDataSource dataSource;
    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");
            this.dataSource = createDataSource();
            this.connection = dataSource.getConnection();

            System.out.println("Connected to database using Azure-managed configuration");

            connectToCache();
            initializeExternalServices();
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private HikariDataSource createDataSource() {
        String dbHost = configurationHelper.getValue("DB_HOST", "localhost");
        String dbPort = configurationHelper.getValue("DB_PORT", "3306");
        String dbName = configurationHelper.getValue("DB_NAME", "mini_app_db");
        String jdbcUrl = configurationHelper.getValue(
                "DB_URL",
                "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                        + "?connectTimeout=10000&socketTimeout=30000&tcpKeepAlive=true");

        String usernameSecretName = configurationHelper.getValue("DB_USERNAME_SECRET_NAME", "db-username");
        String passwordSecretName = configurationHelper.getValue("DB_PASSWORD_SECRET_NAME", "db-password");
        String username = keyVaultHelper.getSecret(usernameSecretName);
        String password = keyVaultHelper.getSecret(passwordSecretName);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setMaximumPoolSize(configurationHelper.getIntValue("DB_MAX_POOL_SIZE", 10));
        hikariConfig.setMinimumIdle(configurationHelper.getIntValue("DB_MIN_IDLE", 2));
        hikariConfig.setConnectionTimeout(configurationHelper.getLongValue("DB_CONNECTION_TIMEOUT_MS", 10000L));
        hikariConfig.setValidationTimeout(configurationHelper.getLongValue("DB_VALIDATION_TIMEOUT_MS", 5000L));
        hikariConfig.setIdleTimeout(configurationHelper.getLongValue("DB_IDLE_TIMEOUT_MS", 600000L));
        hikariConfig.setMaxLifetime(configurationHelper.getLongValue("DB_MAX_LIFETIME_MS", 1800000L));
        hikariConfig.setInitializationFailTimeout(configurationHelper.getLongValue("DB_INITIALIZATION_FAIL_TIMEOUT_MS", 1L));
        hikariConfig.addDataSourceProperty("connectTimeout", String.valueOf(configurationHelper.getLongValue("DB_CONNECT_TIMEOUT_MS", 10000L)));
        hikariConfig.addDataSourceProperty("socketTimeout", String.valueOf(configurationHelper.getLongValue("DB_SOCKET_TIMEOUT_MS", 30000L)));
        hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");

        return new HikariDataSource(hikariConfig);
    }

    private void connectToCache() {
        String redisHost = configurationHelper.getValue("REDIS_HOST", "127.0.0.1");
        int redisPort = configurationHelper.getIntValue("REDIS_PORT", 6379);
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }

    private void initializeExternalServices() {
        String externalApiUrl = configurationHelper.getValue("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
        String paymentServiceUrl = configurationHelper.getValue("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
        Duration connectTimeout = Duration.ofMillis(configurationHelper.getLongValue("HTTP_CONNECT_TIMEOUT_MS", 10000L));
        Duration readTimeout = Duration.ofMillis(configurationHelper.getLongValue("HTTP_READ_TIMEOUT_MS", 30000L));
        Duration writeTimeout = Duration.ofMillis(configurationHelper.getLongValue("HTTP_WRITE_TIMEOUT_MS", 30000L));

        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
        System.out.println("Configured HTTP timeouts - connect: " + connectTimeout.toMillis()
                + "ms, read: " + readTimeout.toMillis()
                + "ms, write: " + writeTimeout.toMillis() + "ms");
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setQueryTimeout(configurationHelper.getIntValue("DB_QUERY_TIMEOUT_SECONDS", DEFAULT_QUERY_TIMEOUT_SECONDS));
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
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    private static final class AzureConfigurationHelper {
        private final ConfigurationClient configurationClient;

        private AzureConfigurationHelper() {
            String connectionString = System.getenv("AZURE_APP_CONFIGURATION_CONNECTION_STRING");
            if (connectionString != null && !connectionString.isBlank()) {
                this.configurationClient = new ConfigurationClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();
            } else {
                this.configurationClient = null;
            }
        }

        private String getValue(String key, String defaultValue) {
            String environmentValue = System.getenv(key);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue;
            }

            if (configurationClient != null) {
                try {
                    String value = configurationClient.getConfigurationSetting(key, null).getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                } catch (RuntimeException ignored) {
                    // Fall back to default value.
                }
            }
            return defaultValue;
        }

        private int getIntValue(String key, int defaultValue) {
            String value = getValue(key, String.valueOf(defaultValue));
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }

        private long getLongValue(String key, long defaultValue) {
            String value = getValue(key, String.valueOf(defaultValue));
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
    }

    private static final class AzureKeyVaultHelper {
        private final SecretClient secretClient;

        private AzureKeyVaultHelper(AzureConfigurationHelper configurationHelper) {
            String keyVaultUrl = configurationHelper.getValue("AZURE_KEY_VAULT_URL", "");
            if (keyVaultUrl.isBlank()) {
                throw new IllegalStateException("AZURE_KEY_VAULT_URL must be configured for secret resolution.");
            }
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            this.secretClient = new SecretClientBuilder()
                    .vaultUrl(keyVaultUrl)
                    .credential(credential)
                    .buildClient();
        }

        private String getSecret(String secretName) {
            String directValue = System.getenv(secretName);
            if (directValue != null && !directValue.isBlank()) {
                return directValue;
            }
            return secretClient.getSecret(secretName).getValue();
        }
    }
}