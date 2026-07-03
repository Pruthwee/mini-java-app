package com.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // REMEDIATION: Replace hard-coded port number with environment variable
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // REMEDIATION: Replace hard-coded file paths with S3 bucket and keys
    private final SsmClient ssmClient;
        this.ssmClient = SsmClient.builder().build();

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
        // REMEDIATION: Reading from S3 instead of hardcoded absolute path
        loadConfiguration();
        
        // REMEDIATION: Writing to S3 instead of hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
            // REMEDIATION: Replace classpath/S3 properties with AWS Systems Manager Parameter Store
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/mini-app/config")
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configData = parameterResponse.parameter().value();
            
            props.load(new java.io.StringReader(configData));
            System.out.println("Configuration loaded from AWS SSM Parameter Store");
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
    }
    
    private void initializeLogging() {
        if (LOG_S3_BUCKET == null) {
            System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
            return;
        }
        try {
            // REMEDIATION: Replace local file creation with S3 PutObject
            String initialLogContent = "Logging initialized at " + java.time.Instant.now().toString() + "\n";
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(initialLogContent));
            System.out.println("Logging initialized in S3: " + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        }
    }
    
    private void startServer() {
        try {
            // REMEDIATION: Use externalized port
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