package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.io.InputStream;
import java.util.Scanner;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
 */
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // Replace hard-coded file paths with S3 bucket and keys
    private static final String CONFIG_S3_BUCKET = System.getenv().getOrDefault("CONFIG_S3_BUCKET", "my-app-config-bucket");
    private final SsmClient ssmClient;
        this.ssmClient = SsmClient.builder().build();
        
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
    
            // FIX: Use AWS Systems Manager Parameter Store to load configuration
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/mini-java-app/config")
                    .withDecryption(true)
                    .build();

            String parameterValue = ssmClient.getParameter(parameterRequest).parameter().value();
            System.out.println("Configuration loaded from AWS SSM Parameter Store: " + parameterValue);
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
    private void initializeLogging() {
        try {
            // FIX: Use S3 to initialize/upload log file
            String initialLogContent = "Application started at " + java.time.LocalDateTime.now() + "\n";
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