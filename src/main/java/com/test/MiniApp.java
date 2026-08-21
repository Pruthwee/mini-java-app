package com.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Mini Java Application with cloud-ready S3 storage integration
 */
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // Cloud-ready: Parameter Store path for configuration
    private static final String PARAMETER_STORE_PATH = System.getenv().getOrDefault("PARAMETER_STORE_PATH", "/mini-app/config/");
    
    // Cloud-ready: S3 bucket and object keys instead of hardcoded file paths
    private static final String S3_BUCKET_NAME = System.getenv().getOrDefault("S3_BUCKET_NAME", "mini-app-config-bucket");
    private static final String LOG_OBJECT_KEY = System.getenv().getOrDefault("LOG_OBJECT_KEY", "logs/mini-app.log");
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
    
    private S3Client s3Client;
    private ParameterStoreService parameterStoreService;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Initialize S3 client for cloud storage
        initializeS3Client();
        
        // Initialize Parameter Store service for externalized configuration
        initializeParameterStore();
        
        // Cloud-native: Load configuration from AWS Systems Manager Parameter Store
        loadConfiguration();
        
        // Cloud-ready: Writing to S3 instead of hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void initializeS3Client() {
        try {
            // Initialize S3 client with default credentials provider (supports IAM roles, environment variables, etc.)
            s3Client = S3Client.builder()
                    .region(Region.of(AWS_REGION))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            System.out.println("S3 client initialized for region: " + AWS_REGION);
        } catch (Exception e) {
            System.err.println("Failed to initialize S3 client: " + e.getMessage());
        }
    }
    
    private void initializeParameterStore() {
        try {
            // Initialize Parameter Store service for externalized configuration
            parameterStoreService = new ParameterStoreService();
            System.out.println("Parameter Store service initialized for path: " + PARAMETER_STORE_PATH);
        } catch (Exception e) {
            System.err.println("Failed to initialize Parameter Store service: " + e.getMessage());
        }
    }
    
    private void loadConfiguration() {
        try {
            // Cloud-native: Load configuration from AWS Systems Manager Parameter Store
            // This replaces classpath-bundled properties files with externalized configuration
            // that can be updated at runtime without redeployment
            if (parameterStoreService != null) {
                Properties props = parameterStoreService.loadConfigurationAsProperties(PARAMETER_STORE_PATH);
                System.out.println("Configuration loaded from AWS Systems Manager Parameter Store: " + PARAMETER_STORE_PATH);
                System.out.println("Loaded " + props.size() + " configuration properties");
                
                // Configuration is now externalized and can be updated in Parameter Store
                // without requiring application redeployment
            } else {
                System.err.println("Parameter Store service not initialized, skipping configuration load");
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
            System.out.println("Warning: Configuration could not be loaded from Parameter Store path: " + PARAMETER_STORE_PATH);
        }
    }
    
    private void initializeLogging() {
        try {
            // Cloud-ready: Initialize logging by creating a log entry in S3 instead of local file system
            String initialLogContent = "Application started at: " + System.currentTimeMillis() + "\n";
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(LOG_OBJECT_KEY)
                    .contentType("text/plain")
                    .build();
            
            s3Client.putObject(putObjectRequest, 
                    RequestBody.fromInputStream(
                            new ByteArrayInputStream(initialLogContent.getBytes(StandardCharsets.UTF_8)),
                            initialLogContent.length()));
            
            System.out.println("Logging initialized in S3: s3://" + S3_BUCKET_NAME + "/" + LOG_OBJECT_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.awsErrorDetails().errorMessage());
        }
    }
    
    private void startServer() {
        try {
            // BLOCKER: Hardcoded port number
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT + " (from environment variable SERVER_PORT)");
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
            // Cleanup S3 client
            if (s3Client != null) {
                s3Client.close();
            }
            
            // Cleanup Parameter Store service
            if (parameterStoreService != null) {
                parameterStoreService.close();
            }
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
