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
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // REMEDIATION: Replace hard-coded file paths with S3 bucket and keys
    private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME") != null ? System.getenv("S3_BUCKET_NAME") : "my-app-config-bucket";
    private static final String CONFIG_S3_KEY = "config/app.properties";
    private static final String LOG_S3_KEY = "logs/mini-app.log";
    
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
            // REMEDIATION: Use AWS Systems Manager Parameter Store to load configuration
            // Instead of loading a properties file from S3 or classpath, we fetch parameters from SSM
            String configParamName = System.getenv("CONFIG_PARAM_NAME") != null ? System.getenv("CONFIG_PARAM_NAME") : "/mini-app/config";
            
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(configParamName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configValue = parameterResponse.parameter().value();
            
            System.out.println("Configuration loaded from AWS SSM Parameter Store: " + configParamName);
            // In a real app, we would parse configValue (e.g., JSON or properties string) into a Properties object
            // For this demonstration, we'll just log that it was loaded.
            
        } catch (Exception e) {
            System.err.println("Failed to load configuration from AWS SSM Parameter Store: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // REMEDIATION: Use S3 to initialize/write log file
            String initialLogContent = "Logging initialized at " + java.time.LocalDateTime.now();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(initialLogContent));
            
            System.out.println("Logging initialized in S3 at: " + S3_BUCKET_NAME + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // BLOCKER: Hardcoded port number
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