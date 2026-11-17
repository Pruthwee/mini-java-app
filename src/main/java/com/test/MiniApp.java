package com.test;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // cr-java-0077: Port externalized to environment variable, old value 8080 as default
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8080"));

    // cr-java-0061: Hard-coded file paths replaced with S3 object storage
    // Bucket and key from environment variables
    private static final String CONFIG_S3_BUCKET = System.getenv("APP_S3_BUCKET");
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_BUCKET = System.getenv("APP_S3_BUCKET");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");

    // cr-java-0070: Properties file loading replaced with SSM Parameter Store
    private static final String APP_PARAMETER_PATH = System.getenv().getOrDefault("APP_PARAMETER_PATH", "/app/config/");

    // Lazy S3 client - built on first use inside try block
    private S3Client s3Client;
    // Lazy SSM client - built on first use inside try block
    private SsmClient ssmClient;

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // cr-java-0070: Configuration loaded from SSM Parameter Store instead of classpath/file
        loadConfiguration();

        // cr-java-0061/0063: Logging initialized via S3 instead of local file
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            // cr-java-0070: Load configuration from AWS SSM Parameter Store
            if (ssmClient == null) {
                ssmClient = SsmClient.create();
            }

            GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                    .path(APP_PARAMETER_PATH)
                    .recursive(true)
                    .withDecryption(true)
                    .build();

            GetParametersByPathResponse response = ssmClient.getParametersByPath(request);

            Properties props = new Properties();
            for (Parameter param : response.parameters()) {
                String key = param.name().replace(APP_PARAMETER_PATH, "");
                props.setProperty(key, param.value());
            }

            System.out.println("Configuration loaded from SSM Parameter Store at: " + APP_PARAMETER_PATH);
        } catch (SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        try {
            // cr-java-0061/0063: Write log initialization to S3 instead of local file
            if (s3Client == null) {
                s3Client = S3Client.create();
            }

            String logContent = "Logging initialized\n";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(LOG_S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(logContent));

            System.out.println("Logging initialized at: " + LOG_S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // cr-java-0077: Port from environment variable
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
