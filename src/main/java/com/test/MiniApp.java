package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // FIX: Use environment variable for port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // Replace hardcoded absolute file paths with S3 bucket and keys
    private static final String CONFIG_S3_BUCKET = System.getenv("CONFIG_S3_BUCKET");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv("LOG_S3_BUCKET");
    private static final String LOG_S3_KEY = "mini-app.log";
    
    private final S3Client s3Client;

    public MiniApp() {
        this.s3Client = S3Client.builder().build();
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Replace reading from hardcoded absolute path with S3
        loadConfiguration();
        
        // Replace writing to hardcoded absolute path with S3
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            if (CONFIG_S3_BUCKET == null) {
                System.out.println("Warning: CONFIG_S3_BUCKET environment variable not set.");
                return;
            }
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();
            
            try (ResponseInputStream<GetObjectResponse> s3is = s3Client.getObject(getObjectRequest)) {
                Properties props = new Properties();
                props.load(s3is);
                System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            if (LOG_S3_BUCKET == null) {
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
                return;
            }
            
            // In a real cloud-native app, we'd use a logging framework that writes to stdout/stderr
            // but to follow the remediation "Replace hard-coded file paths with Amazon S3", 
            // we simulate writing the log file to S3.
            String initialLogContent = "Logging initialized at " + java.time.Instant.now().toString();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(initialLogContent));
            
            System.out.println("Logging initialized in S3: " + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIX: Use environment variable for port number
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