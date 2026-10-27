package com.test;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Properties;

/**
 * Mini Java Application. Port, bucket and object keys come from the environment:
 * PORT, APP_S3_BUCKET, CONFIG_S3_KEY, LOG_S3_KEY and APP_PARAMETER_PATH.
 */
public class MiniApp {

    // Server port: PORT from the environment, with the old value as default
    private static final int DEFAULT_SERVER_PORT = 8080;

    // S3 object keys (in the bucket named by APP_S3_BUCKET) that take the place of the old file paths
    private static final String DEFAULT_CONFIG_S3_KEY = "config/app.properties";
    private static final String DEFAULT_LOG_S3_KEY = "logs/mini-app.log";

    // AWS SDK client timeouts
    private static final Duration SDK_CONNECTION_TIMEOUT = Duration.ofMillis(5000L);
    private static final Duration SDK_SOCKET_TIMEOUT = Duration.ofMillis(30000L);
    private static final Duration SDK_API_CALL_TIMEOUT = Duration.ofMillis(60000L);
    private static final Duration SDK_API_CALL_ATTEMPT_TIMEOUT = Duration.ofMillis(20000L);

    private S3Client s3Client;
    private SsmClient ssmClient;

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        try {
            app.initializeApplication();
            app.startServer();
        } finally {
            app.closeClients();
        }
    }

    private void initializeApplication() {
        // Reading the configuration object from S3
        loadConfiguration();

        // Writing the log object to S3
        initializeLogging();

        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            String bucket = requireBucket();
            String key = envOrDefault("CONFIG_S3_KEY", DEFAULT_CONFIG_S3_KEY);
            String location = "s3://" + bucket + "/" + key;
            if (objectExists(bucket, key)) {
                // Parameter Store values (under APP_PARAMETER_PATH) take precedence over the object's values
                Properties props = loadParameterStoreProperties();
                Properties fromObject = new Properties();
                try (InputStream in = s3().getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
                    fromObject.load(in);
                }
                for (String name : fromObject.stringPropertyNames()) {
                    props.putIfAbsent(name, fromObject.getProperty(name));
                }
                System.out.println("Configuration loaded from: " + location);
            } else {
                System.out.println("Warning: Configuration file not found at: " + location);
            }
        } catch (IOException | SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    private Properties loadParameterStoreProperties() {
        Properties props = new Properties();
        String path = System.getenv("APP_PARAMETER_PATH");
        if (path == null || path.isEmpty()) {
            return props;
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
            response.parameters().forEach(p -> props.setProperty(
                    p.name().startsWith(prefix) ? p.name().substring(prefix.length()) : p.name(), p.value()));
            nextToken = response.nextToken();
        } while (nextToken != null);
        return props;
    }

    private void initializeLogging() {
        try {
            String bucket = requireBucket();
            String logKey = envOrDefault("LOG_S3_KEY", DEFAULT_LOG_S3_KEY);

            if (!objectExists(bucket, logKey)) {
                s3().putObject(PutObjectRequest.builder().bucket(bucket).key(logKey).contentType("text/plain").build(),
                        RequestBody.empty());
            }

            System.out.println("Logging initialized at: s3://" + bucket + "/" + logKey);
        } catch (IOException | SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            int serverPort = resolveServerPort();
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    private static int resolveServerPort() {
        String value = System.getenv("PORT");
        if (value == null || value.isEmpty()) {
            return DEFAULT_SERVER_PORT;
        }
        return Integer.parseInt(value.trim());
    }

    private static String requireBucket() throws IOException {
        String bucket = System.getenv("APP_S3_BUCKET");
        if (bucket == null || bucket.isEmpty()) {
            throw new IOException("APP_S3_BUCKET is not set");
        }
        return bucket;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    private boolean objectExists(String bucket, String key) {
        try {
            s3().headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HeadObject answers 404 without an error body
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    // Clients are built on first use, with the SDK's default chain for region and credentials
    private synchronized S3Client s3() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                    .httpClientBuilder(httpClientBuilder())
                    .overrideConfiguration(overrideConfiguration())
                    .build();
        }
        return s3Client;
    }

    private synchronized SsmClient ssm() {
        if (ssmClient == null) {
            ssmClient = SsmClient.builder()
                    .httpClientBuilder(httpClientBuilder())
                    .overrideConfiguration(overrideConfiguration())
                    .build();
        }
        return ssmClient;
    }

    private static ApacheHttpClient.Builder httpClientBuilder() {
        return ApacheHttpClient.builder()
                .connectionTimeout(SDK_CONNECTION_TIMEOUT)
                .socketTimeout(SDK_SOCKET_TIMEOUT);
    }

    private static ClientOverrideConfiguration overrideConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(SDK_API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(SDK_API_CALL_ATTEMPT_TIMEOUT)
                .build();
    }

    private synchronized void closeClients() {
        if (s3Client != null) {
            s3Client.close();
            s3Client = null;
        }
        if (ssmClient != null) {
            ssmClient.close();
            ssmClient = null;
        }
    }
}
