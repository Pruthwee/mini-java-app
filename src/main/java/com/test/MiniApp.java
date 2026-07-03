package com.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // Replace hard-coded file paths with S3 bucket and keys
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
        // BLOCKER: Reading from hardcoded absolute path -> Now using S3
        loadConfiguration();
        
        // BLOCKER: Writing to hardcoded absolute path -> Now using S3
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        // Use AWS SSM Parameter Store instead of classpath or S3 for configuration
        String parameterPath = System.getenv("CONFIG_SSM_PATH");
        if (parameterPath == null) {
            System.out.println("Warning: CONFIG_SSM_PATH environment variable not set. Falling back to S3 if available.");
            if (CONFIG_S3_BUCKET != null) {
                loadConfigurationFromS3();
            }
            return;
        }
        
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterPath)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configData = parameterResponse.parameter().value();
            
            Properties props = new Properties();
            props.load(new java.io.StringReader(configData));
            System.out.println("Configuration loaded from AWS SSM Parameter Store: " + parameterPath);
        } catch (IOException e) {
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
        }
    }

    private void loadConfigurationFromS3() {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(CONFIG_S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            Properties props = new Properties();
            props.load(s3Object);
            System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
        } catch (IOException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        if (LOG_S3_BUCKET == null) {
            System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
            return;
        }
        try {
            String initialLogContent = "Logging initialized at " + java.time.LocalDateTime.now() + "\n";
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
