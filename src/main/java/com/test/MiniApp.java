package com.test;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // BLOCKER FIXED: Hardcoded port number - now read from environment variable
    private static final int SERVER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // BLOCKER FIXED: Hardcoded absolute file paths - now use S3 and Parameter Store
    private static final String APP_S3_BUCKET = System.getenv("APP_S3_BUCKET");
    private static final String APP_LOG_S3_KEY = System.getenv("APP_LOG_S3_KEY");
    private static final String APP_PARAMETER_PATH = System.getenv("APP_PARAMETER_PATH");
    
    private S3Client s3Client;
    private SsmClient ssmClient;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // BLOCKER FIXED: Reading from hardcoded absolute path - now uses Parameter Store
        loadConfiguration();
        
        // BLOCKER FIXED: Writing to hardcoded absolute path - now uses S3
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // BLOCKER FIXED: Properties file loading replaced with AWS Systems Manager Parameter Store
            if (APP_PARAMETER_PATH == null || APP_PARAMETER_PATH.isEmpty()) {
                System.out.println("Warning: APP_PARAMETER_PATH not set");
                return;
            }
            
            if (ssmClient == null) {
                ssmClient = SsmClient.builder().build();
            }
            
            Properties props = new Properties();
            String nextToken = null;
            do {
                GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                    .path(APP_PARAMETER_PATH)
                    .recursive(true)
                    .withDecryption(true);
                
                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }
                
                GetParametersByPathResponse response = ssmClient.getParametersByPath(requestBuilder.build());
                
                for (Parameter param : response.parameters()) {
                    String key = param.name().replace(APP_PARAMETER_PATH + "/", "");
                    props.setProperty(key, param.value());
                }
                
                nextToken = response.nextToken();
            } while (nextToken != null && !nextToken.isEmpty());
            
            System.out.println("Configuration loaded from Parameter Store: " + APP_PARAMETER_PATH);
        } catch (SsmException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // BLOCKER FIXED: Hardcoded file paths replaced with S3 object storage
            if (APP_S3_BUCKET == null || APP_S3_BUCKET.isEmpty()) {
                System.out.println("Warning: APP_S3_BUCKET not set");
                return;
            }
            
            if (s3Client == null) {
                s3Client = S3Client.builder().build();
            }
            
            String logKey = APP_LOG_S3_KEY != null ? APP_LOG_S3_KEY : "mini-app.log";
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(APP_S3_BUCKET)
                .key(logKey)
                .contentType("text/plain")
                .build();
            
            s3Client.putObject(putRequest, RequestBody.fromString(""));
            
            System.out.println("Logging initialized at: " + APP_S3_BUCKET + "/" + logKey);
        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // BLOCKER FIXED: Hardcoded port number - now uses environment variable
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
