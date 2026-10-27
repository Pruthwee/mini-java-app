package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service - cloud-ready version.
 * Credentials from AWS Secrets Manager (DB_SECRET_NAME), pool via HikariCP with timeouts,
 * ports and hosts from environment variables.
 */
public class DatabaseService {

    // blocker-8,9,10 (cr-java-0069), blocker-16 (cr-java-0113): removed hard-coded DB_URL/DB_USERNAME/DB_PASSWORD.
    //   Overlapping remediations on lines 17-19: Secrets Manager applied first (secret store rule),
    //   then port externalisation (cr-java-0077) applied to the same DB_HOST/DB_PORT construction.
    // blocker-11 (cr-java-0077): removed hard-coded DB_PORT "3306"; read from DB_PORT env var (default 3306).
    // blocker-17 (cr-java-0073): removed hard-coded DB_URL constant; JDBC URL now constructed from env vars.
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "root");
    // DB_SECRET_NAME: name/ARN of the Secrets Manager secret holding {"username":"...","password":"..."}
    // No default - without it the connection fails where a wrong password would have failed.
    private static final String DB_SECRET_NAME = System.getenv("DB_SECRET_NAME");

    // blocker-12 (cr-java-0077): removed hard-coded REDIS_PORT 6379; read from REDIS_PORT env var (default 6379).
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // blocker-17,18 (cr-java-0073): replaced raw JDBC Connection with HikariCP DataSource.
    // blocker-19 (cr-java-0097): HikariCP configured with explicit connection and socket timeouts.
    private HikariDataSource dataSource;

    // Millisecond timeout constants (blocker-19, cr-java-0097)
    private static final int CONNECT_TIMEOUT_MS = 5000;   // 5 seconds TCP connect
    private static final int SOCKET_TIMEOUT_MS  = 30000;  // 30 seconds socket read

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // blocker-8,9,10 (cr-java-0069), blocker-16 (cr-java-0113):
            //   Retrieve DB credentials lazily from Secrets Manager on first use.
            //   SdkException caught beside SQLException — old failure path preserved.
            String dbUsername;
            String dbPassword;
            if (DB_SECRET_NAME != null && !DB_SECRET_NAME.isEmpty()) {
                SecretsManagerClient secretsClient = SecretsManagerClient.builder().build();
                GetSecretValueRequest req = GetSecretValueRequest.builder()
                        .secretId(DB_SECRET_NAME)
                        .build();
                GetSecretValueResponse resp = secretsClient.getSecretValue(req);
                JsonNode secret = objectMapper.readTree(resp.secretString());
                dbUsername = secret.has("username") ? secret.get("username").asText() : DB_USER;
                dbPassword = secret.get("password").asText();
            } else {
                // DB_SECRET_NAME not set: no credential default (cloud-config skill)
                System.err.println("DB_SECRET_NAME is not set; cannot retrieve database credentials");
                return;
            }

            // blocker-17,18 (cr-java-0073), blocker-19 (cr-java-0097):
            //   HikariCP pool replaces DriverManager.getConnection; explicit timeouts added.
            //   initializationFailTimeout(-1) lets the pool start without a live connection.
            // blocker-11 (cr-java-0077): DB_PORT from env var; default 3306.
            String jdbcUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setConnectionTimeout(CONNECT_TIMEOUT_MS);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            // MySQL connectTimeout and socketTimeout are in milliseconds
            config.addDataSourceProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
            config.addDataSourceProperty("socketTimeout",  String.valueOf(SOCKET_TIMEOUT_MS));
            config.addDataSourceProperty("tcpKeepAlive", "true");
            // Start pool without requiring an immediate connection
            config.setInitializationFailTimeout(-1);
            dataSource = new HikariDataSource(config);

            System.out.println("Connection pool initialised for: " + jdbcUrl);
            System.out.println("Using username: " + dbUsername);

            // blocker-12 (cr-java-0077): REDIS_PORT from env var; default 6379.
            connectToCache();

            initializeExternalServices();

        } catch (SdkException e) {
            System.err.println("Failed to retrieve database credentials from Secrets Manager: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during connect: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // blocker-13 (cr-java-0077): REDIS_PORT resolved from REDIS_PORT env var (default 6379).
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
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
