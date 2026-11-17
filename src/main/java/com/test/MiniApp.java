package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    
    // Objects in the Amazon S3 bucket named by APP_S3_BUCKET replace the former local files
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");

    // AWS SDK client timeouts in milliseconds
    private static final long AWS_CONNECT_TIMEOUT_MS = 5000;
    private static final long AWS_SOCKET_TIMEOUT_MS = 30000;
    private static final long AWS_API_CALL_TIMEOUT_MS = 60000;
    private static final long AWS_API_CALL_ATTEMPT_TIMEOUT_MS = 20000;

    private S3Client s3Client;
    private SsmClient ssmClient;
    
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
            String bucket = bucketName();
            String configLocation = "s3://" + bucket + "/" + CONFIG_S3_KEY;
            if (objectExists(bucket, CONFIG_S3_KEY)) {
                Map<String, String> config = loadParameters();
                System.out.println("Configuration loaded from: " + configLocation
                        + " (" + config.size() + " parameters)");
            } else {
                System.out.println("Warning: Configuration file not found at: " + configLocation);
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            String bucket = bucketName();
            String logLocation = "s3://" + bucket + "/" + LOG_S3_KEY;
            if (!objectExists(bucket, LOG_S3_KEY)) {
                s3().putObject(PutObjectRequest.builder().bucket(bucket).key(LOG_S3_KEY).build(),
                        RequestBody.empty());
            }
            
            System.out.println("Logging initialized at: " + logLocation);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private static String bucketName() throws IOException {
        String bucket = System.getenv("APP_S3_BUCKET");
        if (bucket == null || bucket.isEmpty()) {
            throw new IOException("APP_S3_BUCKET is not set");
        }
        return bucket;
    }

    private S3Client s3() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                    .httpClient(ApacheHttpClient.builder()
                            .connectionTimeout(Duration.ofMillis(AWS_CONNECT_TIMEOUT_MS))
                            .socketTimeout(Duration.ofMillis(AWS_SOCKET_TIMEOUT_MS))
                            .build())
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofMillis(AWS_API_CALL_TIMEOUT_MS))
                            .apiCallAttemptTimeout(Duration.ofMillis(AWS_API_CALL_ATTEMPT_TIMEOUT_MS))
                            .build())
                    .build();
        }
        return s3Client;
    }

    private boolean objectExists(String bucket, String key) {
        try {
            s3().headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private SsmClient ssm() {
        if (ssmClient == null) {
            ssmClient = SsmClient.builder()
                    .httpClient(ApacheHttpClient.builder()
                            .connectionTimeout(Duration.ofMillis(AWS_CONNECT_TIMEOUT_MS))
                            .socketTimeout(Duration.ofMillis(AWS_SOCKET_TIMEOUT_MS))
                            .build())
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofMillis(AWS_API_CALL_TIMEOUT_MS))
                            .apiCallAttemptTimeout(Duration.ofMillis(AWS_API_CALL_ATTEMPT_TIMEOUT_MS))
                            .build())
                    .build();
        }
        return ssmClient;
    }

    /** Reads the configuration values from AWS Systems Manager Parameter Store under APP_PARAMETER_PATH. */
    private Map<String, String> loadParameters() throws IOException {
        String path = System.getenv("APP_PARAMETER_PATH");
        if (path == null || path.isEmpty()) {
            throw new IOException("APP_PARAMETER_PATH is not set");
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        Map<String, String> config = new HashMap<>();
        String nextToken = null;
        do {
            GetParametersByPathResponse response = ssm().getParametersByPath(
                    GetParametersByPathRequest.builder()
                            .path(path)
                            .recursive(true)
                            .withDecryption(true)
                            .nextToken(nextToken)
                            .build());
            for (Parameter parameter : response.parameters()) {
                String name = parameter.name();
                config.put(name.startsWith(prefix) ? name.substring(prefix.length()) : name, parameter.value());
            }
            nextToken = response.nextToken();
        } while (nextToken != null);
        return config;
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