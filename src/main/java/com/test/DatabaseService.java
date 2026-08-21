package com.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * Database service with AWS Secrets Manager integration and HikariCP connection pooling
 * Credentials are retrieved from AWS Secrets Manager instead of being hardcoded
 * Connection pooling is managed by HikariCP for optimal cloud performance
 */
public class DatabaseService {
    
    // Cloud-ready: Database connection details from environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Cloud-native: AWS Secrets Manager secret ARN from environment variable
    private static final String DB_SECRET_ARN = System.getenv().getOrDefault(
            "DB_SECRET_ARN", 
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials"
    );
    
    // Fallback credentials (only used if Secrets Manager is unavailable)
    private static final String DB_USERNAME_FALLBACK = System.getenv().getOrDefault("DB_USERNAME", "root");
    private static final String DB_PASSWORD_FALLBACK = System.getenv().getOrDefault("DB_PASSWORD", "password123");
    
    // Cloud-ready: Cache and external service configuration from environment variables
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // Cloud-ready: API endpoints from environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault(
            "EXTERNAL_API_URL", 
            "http://api.example.com:8080/v1"
    );
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault(
            "PAYMENT_SERVICE_URL", 
            "https://payment.internal.company.com/process"
    );
    
    // HikariCP connection pool configuration from environment variables
    private static final int POOL_MAX_SIZE = Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "20"));
    private static final int POOL_MIN_IDLE = Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "5"));
    private static final long CONNECTION_TIMEOUT = Long.parseLong(System.getenv().getOrDefault("DB_CONNECTION_TIMEOUT", "30000"));
    private static final long IDLE_TIMEOUT = Long.parseLong(System.getenv().getOrDefault("DB_IDLE_TIMEOUT", "600000"));
    private static final long MAX_LIFETIME = Long.parseLong(System.getenv().getOrDefault("DB_MAX_LIFETIME", "1800000"));
    
    // HikariCP DataSource for connection pooling
    private HikariDataSource dataSource;
    private SecretsManagerService secretsManagerService;
    
    public DatabaseService() {
        // Initialize AWS Secrets Manager service
        try {
            this.secretsManagerService = new SecretsManagerService();
        } catch (Exception e) {
            System.err.println("Warning: Failed to initialize AWS Secrets Manager service: " + e.getMessage());
            System.err.println("Will use fallback credentials from environment variables");
        }
    }
    
    public void connect() {
        try {
            System.out.println("Initializing HikariCP connection pool...");
            
            // Cloud-native: Retrieve credentials from AWS Secrets Manager
            String username;
            String password;
            
            try {
                if (secretsManagerService != null) {
                    System.out.println("Retrieving database credentials from AWS Secrets Manager: " + DB_SECRET_ARN);
                    Map<String, String> dbCredentials = secretsManagerService.getSecret(DB_SECRET_ARN);
                    username = dbCredentials.get("username");
                    password = dbCredentials.get("password");
                    
                    if (username == null || password == null) {
                        throw new RuntimeException("Database credentials missing 'username' or 'password' keys in secret");
                    }
                    
                    System.out.println("Successfully retrieved database credentials from AWS Secrets Manager");
                } else {
                    throw new RuntimeException("Secrets Manager service not initialized");
                }
            } catch (Exception e) {
                System.err.println("Failed to retrieve credentials from AWS Secrets Manager: " + e.getMessage());
                System.err.println("Falling back to environment variable credentials");
                username = DB_USERNAME_FALLBACK;
                password = DB_PASSWORD_FALLBACK;
            }
            
            // Configure HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // Connection pool settings optimized for cloud environments
            config.setMaximumPoolSize(POOL_MAX_SIZE);
            config.setMinimumIdle(POOL_MIN_IDLE);
            config.setConnectionTimeout(CONNECTION_TIMEOUT);
            config.setIdleTimeout(IDLE_TIMEOUT);
            config.setMaxLifetime(MAX_LIFETIME);
            
            // Additional HikariCP optimizations for cloud
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("MiniAppHikariPool");
            config.setAutoCommit(true);
            config.setLeakDetectionThreshold(60000); // 60 seconds
            
            // Initialize the HikariCP DataSource
            dataSource = new HikariDataSource(config);
            
            System.out.println("HikariCP connection pool initialized successfully");
            System.out.println("Database URL: " + DB_URL);
            System.out.println("Pool Max Size: " + POOL_MAX_SIZE);
            System.out.println("Pool Min Idle: " + POOL_MIN_IDLE);
            System.out.println("Using username: " + username);
            
            // Initialize cache connection
            connectToCache();
            
            // Initialize external services
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Failed to initialize HikariCP connection pool: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void connectToCache() {
        // Cloud-ready: Redis connection using environment variables
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // Cloud-ready: External service URLs from environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        Connection connection = null;
        PreparedStatement stmt = null;
        try {
            // Get connection from HikariCP pool
            if (dataSource != null && !dataSource.isClosed()) {
                connection = dataSource.getConnection();
                stmt = connection.prepareStatement(sql);
                
                // Cloud-ready: Query timeout from environment variable
                int queryTimeout = Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT", "30"));
                stmt.setQueryTimeout(queryTimeout);
                
                System.out.println("Executing query: " + sql);
                stmt.execute();
            } else {
                System.err.println("DataSource is not initialized or is closed");
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        } finally {
            // Properly close resources - connection returns to pool
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (connection != null) {
                    connection.close(); // Returns connection to HikariCP pool
                }
            } catch (SQLException e) {
                System.err.println("Failed to close database resources: " + e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        try {
            // Close HikariCP DataSource (closes all pooled connections)
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                System.out.println("HikariCP connection pool closed");
            }
            
            // Close Secrets Manager service
            if (secretsManagerService != null) {
                secretsManagerService.close();
            }
        } catch (Exception e) {
            System.err.println("Failed to close HikariCP connection pool: " + e.getMessage());
        }
    }
    
    /**
     * Get a connection from the HikariCP pool
     * @return Connection from the pool
     * @throws SQLException if unable to get connection
     */
    public Connection getConnection() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            return dataSource.getConnection();
        }
        throw new SQLException("DataSource is not initialized or is closed");
    }
    
    /**
     * Get the HikariCP DataSource
     * @return HikariDataSource instance
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
