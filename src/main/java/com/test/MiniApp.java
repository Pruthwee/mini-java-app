package com.test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // FIX: Use environment variable for port, with default
    private static final int DEFAULT_SERVER_PORT = 8080;
    
    // FIX: Use S3 bucket and keys instead of absolute paths
    private static final String CONFIG_S3_KEY = "config/app.properties";
    private static final String LOG_S3_KEY = "logs/mini-app.log";
    private static final String S3_BUCKET = System.getenv("S3_BUCKET_NAME");
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();
        
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            SsmClient ssmClient = SsmClient.create();
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/mini-app/config-path")
                    .build();
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configPath = parameterResponse.parameter().value();
            
            System.out.println("Configuration path retrieved from SSM: " + configPath);
            
            // Using S3 for the actual file content as per remediation
            S3Client s3Client = S3Client.create();
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Client.getObject(getObjectRequest)))) {
                Properties props = new Properties();
                props.load(reader);
                System.out.println("Configuration loaded from S3: " + CONFIG_S3_KEY);
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from AWS: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            S3Client s3Client = S3Client.create();
            // In a real cloud app, we'd use a logging framework that writes to CloudWatch
            // But following the remediation to use S3 for file operations
            String logContent = "Log initialized at " + java.time.Instant.now().toString();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(logContent));
            System.out.println("Logging initialized in S3 at: " + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // FIX: Use environment variable for port
            String portStr = System.getenv("SERVER_PORT");
            int port = (portStr != null) ? Integer.parseInt(portStr) : DEFAULT_SERVER_PORT;
            
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + port);
            System.out.println("Server ready to accept connections...");
            
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}