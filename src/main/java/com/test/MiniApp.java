package com.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // BLOCKER FIXED: Hardcoded port number replaced with environment variable
    private static final int SERVER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // BLOCKER FIXED: Hardcoded absolute file paths replaced with S3 object storage
    // Configuration is now loaded from Parameter Store or classpath
    // Log file is now written to S3
    
    // S3 configuration
    private static final String S3_BUCKET = System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-bucket");
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("APP_CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("APP_LOG_S3_KEY", "logs/mini-app.log");
    
    // Parameter Store path for configuration
    private static final String APP_PARAMETER_PATH = System.getenv().getOrDefault("APP_PARAMETER_PATH", "/mini-app/config/");
    
    private S3Client s3Client;
    private SsmClient ssmClient;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // BLOCKER FIXED: Reading from hardcoded absolute path replaced with S3/SSM
        loadConfiguration();
        
        // BLOCKER FIXED: Writing to hardcoded absolute path replaced with S3
        initializeLogging();
        
        // Initialize database connection with externalized values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // BLOCKER FIXED: Hardcoded absolute file path replaced by Parameter Store read
            // First try Parameter Store for externalized configuration
            Properties props = new Properties();
            
            if (ssmClient == null) {
                ssmClient = SsmClient.builder().build();
            }
            
            try {
                GetParameterRequest request = GetParameterRequest.builder()
                    .name(APP_PARAMETER_PATH + "app.properties")
                    .withDecryption(true)
                    .build();
                GetParameterResponse response = ssmClient.getParameter(request);
                String configContent = response.parameter().value();
                props.load(new java.io.ByteArrayInputStream(configContent.getBytes()));
                System.out.println("Configuration loaded from Parameter Store at: " + APP_PARAMETER_PATH + "app.properties");
            } catch (SsmException e) {
                // Fall back to classpath resource (ships with the application)
                InputStream configStream = MiniApp.class.getClassLoader().getResourceAsStream("application.properties");
                if (configStream != null) {
                    props.load(configStream);
                    System.out.println("Configuration loaded from classpath application.properties");
                } else {
                    System.out.println("Warning: Configuration not found in Parameter Store or classpath");
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration from AWS: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // BLOCKER FIXED: Hardcoded absolute path for log file replaced by S3 upload
            if (s3Client == null) {
                s3Client = S3Client.builder().build();
            }
            
            // Write log entry to S3 instead of local file system
            String logEntry = "Logging initialized";
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(S3_BUCKET)
                .key(LOG_S3_KEY)
                .contentType("text/plain")
                .build();
            
            s3Client.putObject(putRequest, RequestBody.fromString(logEntry));
            System.out.println("Logging initialized, writing to S3: " + S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging to S3: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging to AWS: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // BLOCKER FIXED: Hardcoded port number replaced with environment variable
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
