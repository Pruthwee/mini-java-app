package com.test;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Mini Java Application with cloud-ready configuration (AWS)
 */
public class MiniApp {

    // Externalized port configuration via environment variable
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // Externalized S3 configuration via environment variables
    private static final String CONFIG_S3_BUCKET = System.getenv().getOrDefault("APP_S3_BUCKET", "");
    private static final String CONFIG_S3_KEY = System.getenv().getOrDefault("APP_CONFIG_S3_KEY", "prod/application.properties");

    // Temporary directory for local file operations (java.io.tmpdir)
    private static final String LOG_FILE_PATH = System.getProperty("java.io.tmpdir") + "/mini-app.log";

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Load configuration from S3
        loadConfiguration();

        // Initialize logging using temporary directory
        initializeLogging();

        // Initialize database connection with externalized configuration
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        if (CONFIG_S3_BUCKET.isEmpty()) {
            System.out.println("Warning: APP_S3_BUCKET not set, skipping S3 configuration load");
            return;
        }

        S3Client s3Client = null;
        try {
            // S3 client built lazily, using default credentials provider chain
            s3Client = S3Client.builder().build();

            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(CONFIG_S3_BUCKET)
                .key(CONFIG_S3_KEY)
                .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getRequest);
            byte[] data = objectBytes.asByteArray();

            Properties props = new Properties();
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                props.load(inputStream);
            }
            System.out.println("Configuration loaded from S3: " + CONFIG_S3_BUCKET + "/" + CONFIG_S3_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }

    private void initializeLogging() {
        try {
            // Use temporary directory instead of hardcoded /var/log path
            Path logDir = Paths.get(System.getProperty("java.io.tmpdir"));
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            Path logFile = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logFile)) {
                Files.createFile(logFile);
            }

            System.out.println("Logging initialized at: " + LOG_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // Port externalized via environment variable
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
