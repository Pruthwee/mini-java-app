package com.test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // REMEDIATION: Replace hard-coded port with environment variable injection
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // REMEDIATION: Replace hard-coded file paths with S3 bucket and keys
    private static final String CONFIG_S3_KEY = "config/app.properties";
    private final SsmClient ssmClient;
        this.ssmClient = SsmClient.builder().build();
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
            // REMEDIATION: Replace classpath/S3 properties files with AWS Systems Manager Parameter Store
            String parameterName = "/mini-app/config";
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configValue = parameterResponse.parameter().value();

            props.load(new java.io.StringReader(configValue));
            System.out.println("Configuration loaded from AWS SSM Parameter Store: " + parameterName);
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
    }
    
    private void initializeLogging() {
        try {
            if (S3_BUCKET_NAME == null) {
                System.out.println("Warning: S3_BUCKET_NAME environment variable not set.");
                return;
            }

            String logContent = "Logging initialized at " + java.time.Instant.now().toString() + "\n";
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(LOG_S3_KEY)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(logContent));
            
            System.out.println("Logging initialized in S3 at: " + S3_BUCKET_NAME + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // REMEDIATION: Use environment variable for port
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
