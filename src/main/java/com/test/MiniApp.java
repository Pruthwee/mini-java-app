package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Mini Java Application with cloud-ready configuration
 */
public class MiniApp {
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
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
        loadConfiguration();
        initializeLogging();
        
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // REMEDIATION: Replace classpath properties files with AWS Systems Manager Parameter Store
            System.out.println("Loading configuration from AWS Systems Manager Parameter Store...");
            
            // Example of fetching a specific parameter
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/mini-app/config")
                    .build();
            
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String configValue = parameterResponse.parameter().value();
            
            System.out.println("Configuration loaded from SSM: " + configValue);
            
            // If S3 fallback is still needed for the properties file:
            if (CONFIG_S3_BUCKET != null) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(CONFIG_S3_BUCKET)
                        .key(CONFIG_S3_KEY)
                        .build();

                ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
                Properties props = new Properties();
                props.load(s3Object);
                System.out.println("Additional configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from AWS SSM: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            if (LOG_S3_BUCKET == null) {
                System.out.println("Warning: LOG_S3_BUCKET environment variable not set.");
                return;
            }
            String logContent = "Application started at " + java.time.Instant.now().toString();
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
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");
            
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
