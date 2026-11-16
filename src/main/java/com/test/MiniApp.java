package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // The application's own listen port comes from PORT (default 8080)
    private static final int SERVER_PORT = portFromEnvironment("PORT", 8080);
    
    // Object-store location of the configuration file and the log file (bucket from APP_S3_BUCKET)
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");
    
    // Timeouts for the AWS SDK clients
    private static final Duration SDK_API_CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SDK_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(10);
    
    private S3Client s3Client;
    private SsmClient ssmClient;
    
    private static int portFromEnvironment(String name, int defaultPort) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(defaultPort)).trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
        app.closeClients();
    }
    
    private void closeClients() {
        if (s3Client != null) {
            s3Client.close();
            s3Client = null;
        }
        if (ssmClient != null) {
            ssmClient.close();
            ssmClient = null;
        }
    }
    
    private void initializeApplication() {
        // Reading the configuration (S3 object and Parameter Store)
        loadConfiguration();
        
        // Initializing the log file in the object store
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private ClientOverrideConfiguration clientTimeouts() {
        return ClientOverrideConfiguration.builder()
            .apiCallTimeout(SDK_API_CALL_TIMEOUT)
            .apiCallAttemptTimeout(SDK_API_CALL_ATTEMPT_TIMEOUT)
            .build();
    }
    
    private S3Client s3() {
        if (s3Client == null) {
            s3Client = S3Client.builder().overrideConfiguration(clientTimeouts()).build();
        }
        return s3Client;
    }
    
    private SsmClient ssm() {
        if (ssmClient == null) {
            ssmClient = SsmClient.builder().overrideConfiguration(clientTimeouts()).build();
        }
        return ssmClient;
    }
    
    private String bucketName() throws IOException {
        String bucket = System.getenv("APP_S3_BUCKET");
        if (bucket == null || bucket.trim().isEmpty()) {
            throw new IOException("APP_S3_BUCKET is not set");
        }
        return bucket;
    }
    
    private void loadConfiguration() {
        String location = "s3://" + System.getenv("APP_S3_BUCKET") + "/" + CONFIG_S3_KEY;
        try {
            String bucket = bucketName();
            Map<String, String> config = new HashMap<>();
            ResponseBytes<GetObjectResponse> object;
            try {
                object = s3().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(CONFIG_S3_KEY).build());
            } catch (NoSuchKeyException e) {
                System.out.println("Warning: Configuration file not found at: " + location);
                return;
            }
            for (String line : new String(object.asByteArray(), StandardCharsets.UTF_8).split("\\r?\\n")) {
                String entry = line.trim();
                int separator = entry.indexOf('=');
                if (!entry.isEmpty() && !entry.startsWith("#") && separator > 0) {
                    config.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
                }
            }
            // Values kept in Parameter Store (APP_PARAMETER_PATH, read recursively with decryption) take precedence
            config.putAll(readParameterStore());
            System.out.println("Configuration loaded from: " + location);
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private Map<String, String> readParameterStore() {
        Map<String, String> values = new HashMap<>();
        String path = System.getenv("APP_PARAMETER_PATH");
        if (path == null || path.trim().isEmpty()) {
            return values;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        String nextToken = null;
        do {
            GetParametersByPathResponse response = ssm().getParametersByPath(GetParametersByPathRequest.builder()
                .path(path)
                .recursive(true)
                .withDecryption(true)
                .nextToken(nextToken)
                .build());
            response.parameters().forEach(parameter -> {
                String name = parameter.name();
                values.put(name.startsWith(prefix) ? name.substring(prefix.length()) : name, parameter.value());
            });
            nextToken = response.nextToken();
        } while (nextToken != null);
        return values;
    }
    
    private void initializeLogging() {
        try {
            String bucket = bucketName();
            String location = "s3://" + bucket + "/" + LOG_S3_KEY;
            
            // Create the (empty) log object when it does not exist yet
            boolean exists = true;
            try {
                s3().headObject(HeadObjectRequest.builder().bucket(bucket).key(LOG_S3_KEY).build());
            } catch (NoSuchKeyException e) {
                exists = false;
            }
            if (!exists) {
                s3().putObject(PutObjectRequest.builder().bucket(bucket).key(LOG_S3_KEY).contentType("text/plain").build(),
                    RequestBody.fromBytes(new byte[0]));
            }
            
            System.out.println("Logging initialized at: " + location);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // The port comes from PORT (default 8080)
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
