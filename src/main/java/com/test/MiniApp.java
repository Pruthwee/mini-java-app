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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // FIXED: Use environment variable for port, with default
    private static final int DEFAULT_SERVER_PORT = 8080;
    
    // FIXED: Use S3 bucket and keys instead of absolute paths
    private static final String CONFIG_S3_BUCKET = System.getenv("CONFIG_S3_BUCKET");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv("LOG_S3_BUCKET");
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
        // FIXED: Load configuration from S3/SSM
        loadConfiguration();
        
        // FIXED: Initialize logging using S3
        initializeLogging();
        
        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // FIXED: Replace classpath/local file with AWS SSM Parameter Store
            String configValue = ssmClient.getParameter(GetParameterRequest.builder()
                    .name("/app/config/app_properties_path")
                    .build()).parameter().value();
            
            System.out.println("Configuration path retrieved from SSM: " + configValue);
            
            // Also demonstrate S3 read for the actual properties file
            if (CONFIG_S3_BUCKET != null) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(CONFIG_S3_BUCKET)
                        .key(CONFIG_S3_KEY)
                        .build();
                
                try (ResponseInputStream<?> is = s3Client.getObject(getObjectRequest)) {
                    Properties props = new Properties();
                    props.load(is);
                    System.out.println("Configuration loaded from S3 bucket: " + CONFIG_S3_BUCKET);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // FIXED: Replace local file operations with S3
            if (LOG_S3_BUCKET != null) {
                s3Client.putObject(PutObjectRequest.builder()
                        .bucket(LOG_S3_BUCKET)
                        .key(LOG_S3_KEY)
                        .build(), RequestBody.fromString("Log initialized\n"));
                System.out.println("Logging initialized in S3 bucket: " + LOG_S3_BUCKET);
            } else {
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIXED: Replace hardcoded port with environment variable
            String portStr = System.getenv("SERVER_PORT");
            int port = (portStr != null) ? Integer.parseInt(portStr) : DEFAULT_SERVER_PORT;
            
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + port);
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}