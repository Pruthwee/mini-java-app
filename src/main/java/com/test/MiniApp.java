package com.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
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

    // cr-java-0077: port from environment variable with old default
    private static final int SERVER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // cr-java-0061 / cr-java-0063: file paths replaced by S3 object storage.
    // Bucket and key are configuration keys read from the environment.
    private static final String CONFIG_BUCKET = System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-config");
    private static final String CONFIG_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "app.properties");
    private static final String LOG_BUCKET = System.getenv().getOrDefault("APP_LOG_BUCKET", "mini-app-logs");
    private static final String LOG_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "mini-app.log");

    // cr-java-0070: configuration path in Parameter Store (hierarchical, with decryption)
    private static final String APP_PARAMETER_PATH = System.getenv().getOrDefault("APP_PARAMETER_PATH", "/mini-app/config/");

    // S3 and SSM clients are built lazily on first use inside the old try blocks
    private S3Client s3Client;
    private SsmClient ssmClient;

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // cr-java-0061 / cr-java-0063: configuration loaded from S3 instead of local file
        loadConfiguration();

        // cr-java-0061 / cr-java-0063: logging initialized via S3 instead of local file
        initializeLogging();

        // cr-java-0070: load configuration from Parameter Store instead of classpath properties
        loadConfigurationFromParameterStore();

        // Initialize database connection with externalized credentials
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        // cr-java-0061: Hardcoded absolute file path /opt/app/config/app.properties
        // replaced by an S3 GetObject read; bucket and key from APP_S3_BUCKET and CONFIG_S3_KEY
        try {
            if (s3Client == null) {
                s3Client = buildS3Client();
            }

            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(CONFIG_BUCKET)
                .key(CONFIG_KEY)
                .build();

            Properties props = new Properties();
            try (InputStream configStream = s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asInputStream()) {
                props.load(configStream);
                System.out.println("Configuration loaded from S3: " + CONFIG_BUCKET + "/" + CONFIG_KEY);
            }
        } catch (NoSuchKeyException e) {
            System.out.println("Warning: Configuration object not found at: " + CONFIG_BUCKET + "/" + CONFIG_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        // cr-java-0061 / cr-java-0063: Hardcoded absolute path /var/log/mini-app.log
        // replaced by an S3 PutObject write; bucket and key from APP_LOG_BUCKET and LOG_S3_KEY
        try {
            if (s3Client == null) {
                s3Client = buildS3Client();
            }

            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(LOG_BUCKET)
                .key(LOG_KEY)
                .contentType("text/plain")
                .build();

            s3Client.putObject(putRequest, RequestBody.fromString("Logging initialized at: " + LOG_KEY));

            System.out.println("Logging initialized at: " + LOG_BUCKET + "/" + LOG_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging to S3: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging to S3: " + e.getMessage());
        }
    }

    private void loadConfigurationFromParameterStore() {
        // cr-java-0070: classpath properties file replaced by Parameter Store
        try {
            if (ssmClient == null) {
                ssmClient = buildSsmClient();
            }

            GetParameterRequest request = GetParameterRequest.builder()
                .name(APP_PARAMETER_PATH)
                .withDecryption(true)
                .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            System.out.println("Configuration loaded from Parameter Store: " + response.parameter().name());
        } catch (SsmException e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // cr-java-0077: Hardcoded port 8080 replaced by SERVER_PORT env var
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

    private S3Client buildS3Client() {
        // cr-java-0061 / cr-java-0063: AWS SDK v2 client, region and credentials from default chain
        return S3Client.create();
    }

    private SsmClient buildSsmClient() {
        // cr-java-0070: AWS SDK v2 SSM client, region and credentials from default chain
        return SsmClient.create();
    }
}
