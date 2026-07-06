package com.test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // FIX: Replace hardcoded port with environment variable
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // FIX: Replace hardcoded paths with S3 bucket/keys
    private static final String CONFIG_S3_BUCKET = System.getenv().getOrDefault("CONFIG_S3_BUCKET", "my-app-config-bucket");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv().getOrDefault("LOG_S3_BUCKET", "my-app-logs-bucket");
    private static final String LOG_S3_KEY = "mini-app.log";
    
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        this.s3Client = S3Client.create();
        this.ssmClient = SsmClient.create();
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // FIX: Load configuration from S3/SSM
        loadConfiguration();
        
        // FIX: Initialize logging using S3
        initializeLogging();
        
        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // FIX: Replace classpath/local file with AWS SSM Parameter Store or S3
            // Using S3 as per remediation for file paths, but also SSM for properties
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();
            
            Properties props = new Properties();
            s3Client.getObject(getObjectRequest, (inputStream) -> {
                props.load(inputStream);
                return null;
            });
            
            System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
            // Fallback to SSM for specific parameters if needed
        }
    }
    
    private void initializeLogging() {
        try {
            // FIX: Replace local file operations with S3
            String logContent = "Application started at " + java.time.LocalDateTime.now();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(logContent));
            
            System.out.println("Logging initialized in S3: " + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIX: Use environment variable for port
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
