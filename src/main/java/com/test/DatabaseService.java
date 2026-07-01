package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

public class DatabaseService {
    private Connection connection;
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final String redisHost;
    private final int redisPort;
    private final String externalApiUrl;
    private final String paymentServiceUrl;

    public DatabaseService() {
        this.dbUrl = Optional.ofNullable(System.getenv("DB_URL")).orElse("jdbc:mysql://localhost:3306/mini_app_db");
        this.dbUsername = Optional.ofNullable(System.getenv("DB_USERNAME")).orElse("root");
        this.dbPassword = Optional.ofNullable(System.getenv("DB_PASSWORD")).orElse("password123");
        this.redisHost = Optional.ofNullable(System.getenv("REDIS_HOST")).orElse("127.0.0.1");
        this.redisPort = Integer.parseInt(Optional.ofNullable(System.getenv("REDIS_PORT")).orElse("6379"));
        this.externalApiUrl = Optional.ofNullable(System.getenv("EXTERNAL_API_URL")).orElse("http://api.example.com:8080/v1");
        this.paymentServiceUrl = Optional.ofNullable(System.getenv("PAYMENT_SERVICE_URL")).orElse("https://payment.internal.company.com/process");
        
        initializeConnection();
    }

    private void initializeConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            System.out.println("Connected to database: " + dbUrl);
            System.out.println("Using username: " + dbUsername);
            
            connectToCache();
            initializeExternalServices();
            
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
    
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
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
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
