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
    private static final int SERVER_PORT = Integer.parseInt(System.getenv("SERVER_PORT") != null ? System.getenv("SERVER_PORT") : "8080");
    
    // Replace hardcoded absolute file paths with S3 bucket and keys
    private static final String CONFIG_S3_BUCKET = System.getenv("CONFIG_S3_BUCKET");
    private static final String CONFIG_S3_KEY = "app.properties";
    private static final String LOG_S3_BUCKET = System.getenv("LOG_S3_BUCKET");
    private static final String LOG_S3_KEY = "mini-app.log";
    
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        this.s3Client = S3Client.create();
        this.ssmClient = SsmClient.create();
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
            // REMEDIATION: Replace classpath properties files with AWS Systems Manager Parameter Store
            // Instead of loading from S3 or classpath, we now fetch configuration from AWS SSM Parameter Store
            System.out.println("Loading configuration from AWS Systems Manager Parameter Store...");
            
            Properties props = new Properties();
            
            // Example of fetching a specific parameter. In a real app, you might fetch a path of parameters.
            String parameterName = "/mini-app/config/server.port";
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String value = parameterResponse.parameter().value();
            
            props.setProperty("server.port", value);
            System.out.println("Configuration loaded from SSM: " + parameterName + " = " + value);
            
        } catch (Exception e) {
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
            
            // Fallback to S3 if SSM fails (optional, but keeping logic similar to original)
            fallbackLoadFromS3();
        }
    }

    private void fallbackLoadFromS3() {
        try {
            if (CONFIG_S3_BUCKET == null) {
                System.out.println("Warning: CONFIG_S3_BUCKET environment variable not set.");
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
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
                return;
            }
            
            // In a real cloud-native app, we'd use a logging framework that writes to stdout/stderr
            // and is collected by CloudWatch/Fluentd. For this remediation, we simulate writing a log file to S3.
            String initialLogContent = "Logging initialized at " + java.time.Instant.now().toString();
            
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