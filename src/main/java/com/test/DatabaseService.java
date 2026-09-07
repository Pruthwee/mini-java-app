package com.test;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service - Cloud-ready version with HikariCP connection pooling,
 * Azure Key Vault for secrets, and externalized configuration
 */
public class DatabaseService {

    // Cloud-ready: Configuration from environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Cloud-ready: Redis configuration from environment variables
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // Cloud-ready: External API endpoints from environment variables
    private static final String EXTERNAL_API_URL = System.getenv()
            .getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv()
            .getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // Cloud-ready: Azure Key Vault configuration
    private static final String KEY_VAULT_URL = System.getenv("AZURE_KEY_VAULT_URL");

    // Cloud-ready: HikariCP connection pool
    private HikariDataSource dataSource;

    // Cloud-ready: Query timeout from environment variable
    private static final int QUERY_TIMEOUT_SECONDS = Integer.parseInt(
            System.getenv().getOrDefault("DB_QUERY_TIMEOUT_SECONDS", "30"));

    // Cloud-ready: Connection timeout from environment variable
    private static final int CONNECTION_TIMEOUT_MS = Integer.parseInt(
            System.getenv().getOrDefault("DB_CONNECTION_TIMEOUT_MS", "30000"));

    // Cloud-ready: Azure credential for Key Vault access
    private static final DefaultAzureCredential azureCredential =
            new DefaultAzureCredentialBuilder().build();

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Cloud-ready: Retrieve credentials from Azure Key Vault
            SecretClient secretClient = new SecretClientBuilder()
                    .vaultUrl(KEY_VAULT_URL)
                    .credential(azureCredential)
                    .buildClient();

            // Retrieve database credentials from Azure Key Vault
            String dbUsername = secretClient.getSecret("DB-USERNAME").getValue();
            String dbPassword = secretClient.getSecret("DB-PASSWORD").getValue();

            // Cloud-ready: Configure HikariCP connection pool with Azure SQL Database resiliency
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Cloud-ready: Connection pool sizing from environment variables
            config.setMaximumPoolSize(Integer.parseInt(
                    System.getenv().getOrDefault("DB_MAX_POOL_SIZE", "20")));
            config.setMinimumIdle(Integer.parseInt(
                    System.getenv().getOrDefault("DB_MIN_IDLE", "5")));

            // Cloud-ready: Connection timeout configuration
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setIdleTimeout(Duration.ofSeconds(60).toMillis());
            config.setMaxLifetime(Duration.ofMinutes(30).toMillis());

            // Cloud-ready: Azure SQL Database connection resiliency
            config.addDataSourceProperty("socketTimeout", "30000");
            config.addDataSourceProperty("connectTimeout", "30000");
            config.addDataSourceProperty("loginTimeout", "30000");
            config.addDataSourceProperty("queryTimeout", String.valueOf(QUERY_TIMEOUT_SECONDS));

            dataSource = new HikariDataSource(config);

            // Cloud-ready: Get connection from HikariCP pool
            connection = dataSource.getConnection();

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using HikariCP connection pool");

            // Cloud-ready: Redis configuration from environment variables
            connectToCache();

            // Cloud-ready: External service URLs from environment variables
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // Cloud-ready: Redis connection details from environment variables
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // Cloud-ready: External service URLs from environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);

                // Cloud-ready: Query timeout from environment variable
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

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
            // Cloud-ready: Properly close HikariCP data source
            if (dataSource != null) {
                dataSource.close();
                System.out.println("HikariCP connection pool closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
