package com.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // FIXED: Hardcoded port number replaced with environment variable
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // BLOCKER: Hardcoded absolute file path - Now treated as S3 keys
    private static final String CONFIG_FILE_KEY = "config/app.properties";
    private static final String LOG_FILE_KEY = "logs/mini-app.log";
    private static final String BUCKET_NAME = System.getenv("S3_BUCKET_NAME") != null ? System.getenv("S3_BUCKET_NAME") : "my-app-bucket";
    
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        this.s3Client = S3Client.builder().build();
        this.ssmClient = SsmClient.builder().build();
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // BLOCKER: Reading from hardcoded absolute path
        loadConfiguration();
        
        // BLOCKER: Writing to hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // FIXED: Replaced classpath/S3 properties file with AWS Systems Manager Parameter Store
            System.out.println("Loading configuration from AWS SSM Parameter Store...");
            
            // Assuming configuration is stored as a single parameter containing properties or multiple parameters
            // For this remediation, we simulate fetching a parameter that would replace the properties file
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/mini-app/config")
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configData = parameterResponse.parameter().value();
            
            Properties props = new Properties();
            props.load(new java.io.StringReader(configData));
            
            System.out.println("Configuration loaded from AWS SSM Parameter Store");
        } catch (Exception e) {
            System.err.println("Failed to load configuration from SSM: " + e.getMessage());
            // Fallback to S3 if SSM fails, or handle as critical error
            loadConfigurationFromS3();
        }
    }

    private void loadConfigurationFromS3() {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(CONFIG_FILE_KEY)
                    .build();
            
            try (InputStream is = s3Client.getObject(getObjectRequest)) {
                Properties props = new Properties();
                props.load(is);
                System.out.println("Configuration loaded from S3: " + BUCKET_NAME + "/" + CONFIG_FILE_KEY);
            }
        } catch (S3Exception e) {
            System.out.println("Warning: Configuration file not found in S3: " + CONFIG_FILE_KEY);
        } catch (IOException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // FIXED: Replaced java.io.File with S3 client call
            // In a real cloud app, we'd use a logging framework that writes to stdout/cloudwatch,
            // following the remediation to use S3 for persistent storage.
            String initialLogContent = "Logging initialized at " + java.time.Instant.now() + "\n";
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(LOG_FILE_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(initialLogContent));
            
            System.out.println("Logging initialized in S3 at: " + BUCKET_NAME + "/" + LOG_FILE_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIXED: Hardcoded port number replaced with environment variable
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
