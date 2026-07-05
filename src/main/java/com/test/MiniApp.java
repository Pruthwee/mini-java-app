package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.core.ResponseBytes;
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
    
    // FIX: Replace hardcoded port number with environment variable
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // FIX: Replace hardcoded absolute file paths with S3 bucket/key
    private static final String CONFIG_S3_BUCKET = System.getenv().getOrDefault("CONFIG_S3_BUCKET", "my-app-config-bucket");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv().getOrDefault("LOG_S3_BUCKET", "my-app-logs-bucket");
    private static final String LOG_S3_KEY = "mini-app.log";
    
    private final S3Client s3Client = S3Client.create();
    private final SsmClient ssmClient = SsmClient.create();
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // FIX: Reading from S3 instead of hardcoded absolute path
        loadConfiguration();
        
        // FIX: Writing to S3 instead of hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // FIX: Replace classpath properties files with AWS Systems Manager Parameter Store or S3
            // Using S3 as per remediation for file paths, but also considering SSM for properties
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();
            
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            String configContent = objectBytes.asUtf8String();
            
            Properties props = new Properties();
            props.load(new java.io.StringReader(configContent));
            System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
            // Fallback to SSM for specific parameters if needed
        }
    }
    
    private void initializeLogging() {
        try {
            // FIX: Replace local file operations with S3
            String logMessage = "Logging initialized at " + java.time.Instant.now().toString();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(logMessage));
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