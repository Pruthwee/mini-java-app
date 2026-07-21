package com.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // Replace hardcoded absolute file paths with S3 bucket and keys
    private static final String CONFIG_S3_BUCKET = System.getenv("CONFIG_S3_BUCKET");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv("LOG_S3_BUCKET");
    private final SsmClient ssmClient;
        this.ssmClient = SsmClient.builder().build();
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // BLOCKER: Reading from hardcoded absolute path -> Now using S3
        loadConfiguration();
        
        // BLOCKER: Writing to hardcoded absolute path -> Now using S3
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
            String parameterName = System.getenv("APP_CONFIG_PARAMETER_NAME");
            if (parameterName == null) {
                System.out.println("Warning: APP_CONFIG_PARAMETER_NAME environment variable not set.");
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configValue = parameterResponse.parameter().value();
            System.out.println("Configuration loaded from AWS SSM Parameter Store: " + parameterName);
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
    private void initializeLogging() {
        try {
            if (LOG_S3_BUCKET == null) {
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
                return;
            }

            String initialLogContent = "Logging initialized at " + java.time.Instant.now() + "\n";
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
