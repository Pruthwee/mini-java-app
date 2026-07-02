package com.test;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service - Cloud-ready version with HikariCP and Azure Key Vault integration
 */
public class DatabaseService {
    
    // Cloud-ready: Configuration from environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Cloud-ready: Azure Key Vault configuration
    private static final String KEY_VAULT_URL = System.getenv().getOrDefault(
        "AZURE_KEY_VAULT_URL", 
        "https://your-keyvault.vault.azure.net/"
    );
    private static final String DB_USERNAME_SECRET_NAME = System.getenv().getOrDefault(
        "DB_USERNAME_SECRET_NAME", 
        "db-username"
    );
    private static final String DB_PASSWORD_SECRET_NAME = System.getenv().getOrDefault(
        "DB_PASSWORD_SECRET_NAME", 
        "db-password"
    );
    
    // Cloud-ready: Cache configuration from environment
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
        System.getenv().getOrDefault("REDIS_PORT", "6379")
    );
    
    // Cloud-ready: External service URLs from environment
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault(
        "EXTERNAL_API_URL", 
        "http://api.example.com:8080/v1"
    );
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault(
        "PAYMENT_SERVICE_URL", 
        "https://payment.internal.company.com/process"
    );
    
    // Cloud-ready: Connection timeout from environment
    private static final int CONNECTION_TIMEOUT = Integer.parseInt(
        System.getenv().getOrDefault("DB_CONNECTION_TIMEOUT_MS", "30000")
    );
    private static final int QUERY_TIMEOUT = Integer.parseInt(
        System.getenv().getOrDefault("DB_QUERY_TIMEOUT_SEC", "30")
    );
    
    private HikariDataSource dataSource;
    private SecretClient secretClient;
    
    public void connect() {
        try {
            System.out.println("Connecting to database using HikariCP...");
            
            // Initialize Azure Key Vault client for secure credential retrieval
            initializeKeyVaultClient();
            
            // Retrieve credentials from Azure Key Vault
            String dbUsername = getSecretFromKeyVault(DB_USERNAME_SECRET_NAME, "root");
            String dbPassword = getSecretFromKeyVault(DB_PASSWORD_SECRET_NAME, "password123");
            
            // Configure HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // Cloud-ready: Connection pool settings from environment
            config.setMaximumPoolSize(Integer.parseInt(
                System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "10")
            ));
            config.setMinimumIdle(Integer.parseInt(
                System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "2")
            ));
            config.setConnectionTimeout(CONNECTION_TIMEOUT);
            config.setIdleTimeout(Integer.parseInt(
                System.getenv().getOrDefault("DB_IDLE_TIMEOUT_MS", "600000")
            ));
            config.setMaxLifetime(Integer.parseInt(
                System.getenv().getOrDefault("DB_MAX_LIFETIME_MS", "1800000")
            ));
            
            // Cloud-ready: Connection validation and resilience
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(5000);
            config.setLeakDetectionThreshold(60000);
            
            // Additional Azure SQL Database optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            
            // Initialize HikariCP data source
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("HikariCP connection pool initialized with max size: " + config.getMaximumPoolSize());
            
            // Cloud-ready: Cache connection with environment configuration
            connectToCache();
            
            // Cloud-ready: External service URLs from environment
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeKeyVaultClient() {
        try {
            // Use Managed Identity for Azure Key Vault authentication
            secretClient = new SecretClientBuilder()
                .vaultUrl(KEY_VAULT_URL)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
            
            System.out.println("Azure Key Vault client initialized: " + KEY_VAULT_URL);
        } catch (Exception e) {
            System.err.println("Failed to initialize Azure Key Vault client: " + e.getMessage());
            System.err.println("Falling back to environment variables for credentials");
        }
    }
    
    private String getSecretFromKeyVault(String secretName, String fallbackValue) {
        try {
            if (secretClient != null) {
                KeyVaultSecret secret = secretClient.getSecret(secretName);
                System.out.println("Retrieved secret from Key Vault: " + secretName);
                return secret.getValue();
            }
        } catch (Exception e) {
            System.err.println("Failed to retrieve secret from Key Vault: " + secretName);
            System.err.println("Using fallback value or environment variable");
        }
        
        // Fallback to environment variable
        return System.getenv().getOrDefault(secretName.toUpperCase().replace("-", "_"), fallbackValue);
    }
    
    private void connectToCache() {
        // Cloud-ready: Redis connection details from environment
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection with cloud-ready configuration
    }
    
    private void initializeExternalServices() {
        // Cloud-ready: External service URLs from environment
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        Connection connection = null;
        PreparedStatement stmt = null;
        
        try {
            if (dataSource != null) {
                // Get connection from HikariCP pool
                connection = dataSource.getConnection();
                
                stmt = connection.prepareStatement(sql);
                
                // Cloud-ready: Query timeout from environment
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                
                System.out.println("Executing query: " + sql);
                stmt.execute();
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        } finally {
            // Properly close resources
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (connection != null) {
                    connection.close(); // Returns connection to pool
                }
            } catch (SQLException e) {
                System.err.println("Failed to close resources: " + e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                System.out.println("HikariCP connection pool closed");
            }
        } catch (Exception e) {
            System.err.println("Failed to close connection pool: " + e.getMessage());
        }
    }
}
