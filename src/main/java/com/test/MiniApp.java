package com.test;

import java.io.IOException;
import java.io.InputStream;
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
    
    // REMEDIATION: Replace hard-coded ports with AWS Parameter Store and environment variable injection
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // REMEDIATION: Replace hard-coded file paths with S3 bucket and keys
    private static final String CONFIG_S3_BUCKET = System.getenv("CONFIG_S3_BUCKET");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv("LOG_S3_BUCKET");
    private static final String LOG_S3_KEY = "mini-app.log";
    
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
        // REMEDIATION: Reading from S3 instead of hardcoded absolute path
        loadConfiguration();
        
        // REMEDIATION: Writing to S3 instead of hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // REMEDIATION: Replace classpath properties files with AWS Systems Manager Parameter Store
            // Instead of loading from S3 or classpath, we now fetch configuration from AWS SSM Parameter Store
            System.out.println("Loading configuration from AWS SSM Parameter Store...");
            
            // Example: Fetching a specific parameter. In a real app, you might fetch a path of parameters.
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
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
            // Fallback to S3 if SSM fails (optional, but keeping logic consistent with previous state)
            loadConfigurationFromS3();
        }
    }

    private void loadConfigurationFromS3() {
        try {
            if (CONFIG_S3_BUCKET == null) {
                System.out.println("Warning: CONFIG_S3_BUCKET environment variable not set");
                return;
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> s3InputStream = s3Client.getObject(getObjectRequest);
            Properties props = new Properties();
            props.load(s3InputStream);
            System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
            
        } catch (Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            if (LOG_S3_BUCKET == null) {
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set");
                return;
            }

            String logContent = "Logging initialized at " + java.time.Instant.now().toString() + "\n";
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
            // REMEDIATION: Replace hard-coded ports with AWS Parameter Store and environment variable injection
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
