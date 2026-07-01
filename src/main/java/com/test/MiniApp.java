package com.test;

import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import java.io.InputStream;

/**
 * Mini Java Application with containerization fixes applied
 */
public class MiniApp {
    
    // FIXED: Externalized port configuration using environment variable
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // FIXED: Replaced absolute file paths with S3 bucket and key references
    private static final String CONFIG_S3_BUCKET = System.getenv().getOrDefault("CONFIG_S3_BUCKET", "app-config-bucket");
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_BUCKET = System.getenv().getOrDefault("LOG_S3_BUCKET", "app-logs-bucket");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");
    
    private S3Client s3Client;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Initialize S3 client for cloud storage
        s3Client = S3Client.builder().build();
        
        // FIXED: Reading from S3 instead of hardcoded absolute path
        loadConfiguration();
        
        // FIXED: Writing to S3 instead of hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with environment variables
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // FIXED: Load configuration from S3 instead of absolute file path
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();
            
            try (InputStream s3InputStream = s3Client.getObject(getObjectRequest)) {
                Properties props = new Properties();
                props.load(s3InputStream);
                System.out.println("Configuration loaded from S3: s3://" + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
            System.out.println("Using default configuration");
        }
    }
    
    private void initializeLogging() {
        try {
            // FIXED: Write logs to S3 instead of hardcoded absolute path
            String logMessage = "Application initialized at " + System.currentTimeMillis();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(logMessage));
            
            System.out.println("Logging initialized to S3: s3://" + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging to S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIXED: Using externalized port configuration from environment variable
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
