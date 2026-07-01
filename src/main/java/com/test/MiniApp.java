package com.test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application - Cloud-ready version with AWS integrations
 * Fixed to use AWS S3 for file storage, Parameter Store for configuration,
 * and environment variables for dynamic port configuration
 */
public class MiniApp {
    
    // FIXED: Use environment variable for port configuration (cr-java-0077)
    private static final int SERVER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("SERVER_PORT", "8080")
    );
    
    // FIXED: Replace hardcoded file paths with S3 bucket and keys (cr-java-0061)
    private static final String S3_BUCKET_NAME = System.getenv().getOrDefault("S3_BUCKET_NAME", "mini-app-config-bucket");
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_KEY_PREFIX = System.getenv().getOrDefault("LOG_S3_KEY_PREFIX", "logs/");
    
    // AWS Region configuration
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
    
    // AWS clients
    private S3Client s3Client;
    private SsmClient ssmClient;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application (Cloud-Ready)...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Initialize AWS clients
        initializeAwsClients();
        
        // FIXED: Load configuration from S3 instead of local file system (cr-java-0061, cr-java-0063)
        loadConfigurationFromS3();
        
        // FIXED: Initialize logging to S3 instead of local file system (cr-java-0061, cr-java-0063)
        initializeLoggingToS3();
        
        // Initialize database connection with cloud-native configuration
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void initializeAwsClients() {
        try {
            Region region = Region.of(AWS_REGION);
            
            // Initialize S3 client
            s3Client = S3Client.builder()
                .region(region)
                .build();
            
            // Initialize Systems Manager client for Parameter Store
            ssmClient = SsmClient.builder()
                .region(region)
                .build();
            
            System.out.println("AWS clients initialized for region: " + AWS_REGION);
        } catch (Exception e) {
            System.err.println("Failed to initialize AWS clients: " + e.getMessage());
        }
    }
    
    /**
     * FIXED: Replace hardcoded file path with S3 object storage (cr-java-0061, cr-java-0063)
     * Load configuration from S3 bucket instead of local file system
     */
    private void loadConfigurationFromS3() {
        try {
            // FIXED: Read configuration from S3 instead of local file (cr-java-0063)
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(S3_BUCKET_NAME)
                .key(CONFIG_S3_KEY)
                .build();
            
            InputStream configStream = s3Client.getObject(getObjectRequest);
            Properties props = new Properties();
            props.load(configStream);
            
            System.out.println("Configuration loaded from S3: s3://" + S3_BUCKET_NAME + "/" + CONFIG_S3_KEY);
            
            configStream.close();
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
            System.out.println("Attempting to load configuration from Parameter Store as fallback...");
            loadConfigurationFromParameterStore();
        }
    }
    
    /**
     * FIXED: Load configuration from AWS Parameter Store (cr-java-0070)
     * Externalized configuration instead of classpath properties
     */
    private void loadConfigurationFromParameterStore() {
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name("/mini-app/config")
                .withDecryption(true)
                .build();
            
            GetParameterResponse response = ssmClient.getParameter(parameterRequest);
            String configValue = response.parameter().value();
            
            System.out.println("Configuration loaded from Parameter Store: /mini-app/config");
        } catch (Exception e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
        }
    }
    
    /**
     * FIXED: Replace local file logging with S3 storage (cr-java-0061, cr-java-0063)
     * Write logs to S3 instead of local file system
     */
    private void initializeLoggingToS3() {
        try {
            // FIXED: Write log initialization marker to S3 instead of local file (cr-java-0063)
            String logMessage = "Application started at: " + System.currentTimeMillis() + "\n";
            String logKey = LOG_S3_KEY_PREFIX + "app-" + System.currentTimeMillis() + ".log";
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(S3_BUCKET_NAME)
                .key(logKey)
                .contentType("text/plain")
                .build();
            
            s3Client.putObject(putObjectRequest, 
                RequestBody.fromInputStream(
                    new ByteArrayInputStream(logMessage.getBytes(StandardCharsets.UTF_8)),
                    logMessage.length()
                ));
            
            System.out.println("Logging initialized to S3: s3://" + S3_BUCKET_NAME + "/" + logKey);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging to S3: " + e.getMessage());
        }
    }
    
    /**
     * FIXED: Use environment variable for port configuration (cr-java-0077)
     * Port is now configurable via SERVER_PORT environment variable
     */
    private void startServer() {
        try {
            // FIXED: Port from environment variable instead of hardcoded (cr-java-0077)
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT + " (from environment variable)");
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        } finally {
            // Clean up AWS clients
            cleanup();
        }
    }
    
    private void cleanup() {
        try {
            if (s3Client != null) {
                s3Client.close();
            }
            if (ssmClient != null) {
                ssmClient.close();
            }
            System.out.println("AWS clients closed successfully");
        } catch (Exception e) {
            System.err.println("Error closing AWS clients: " + e.getMessage());
        }
    }
}
