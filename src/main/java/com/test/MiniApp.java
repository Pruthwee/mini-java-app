package com.test;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.TokenCredential;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Mini Java Application - Cloud-ready version with Azure Blob Storage and externalized configuration
 */
public class MiniApp {

    // Cloud-ready: Port from environment variable with fallback
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // Cloud-ready: Azure Blob Storage configuration from environment variables
    private static final String AZURE_STORAGE_CONNECTION_STRING =
            System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    private static final String AZURE_STORAGE_ACCOUNT =
            System.getenv().getOrDefault("AZURE_STORAGE_ACCOUNT", "miniastorage");
    private static final String BLOB_CONTAINER_NAME =
            System.getenv().getOrDefault("BLOB_CONTAINER_NAME", "mini-app-config");
    private static final String CONFIG_BLOB_NAME =
            System.getenv().getOrDefault("CONFIG_BLOB_NAME", "app.properties");
    private static final String LOG_BLOB_NAME =
            System.getenv().getOrDefault("LOG_BLOB_NAME", "mini-app.log");

    // Azure credential for secure access
    private static final DefaultAzureCredential azureCredential =
            new DefaultAzureCredentialBuilder().build();

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Cloud-ready: Load configuration from Azure Blob Storage
        loadConfiguration();

        // Cloud-ready: Initialize logging to Azure Blob Storage
        initializeLogging();

        // Initialize database connection with externalized credentials
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            // Cloud-ready: Load configuration from Azure Blob Storage instead of local file system
            BlobClient blobClient = new BlobClientBuilder()
                    .connectionString(AZURE_STORAGE_CONNECTION_STRING)
                    .containerName(BLOB_CONTAINER_NAME)
                    .blobName(CONFIG_BLOB_NAME)
                    .buildClient();

            if (blobClient.exists()) {
                // Download configuration from Azure Blob Storage
                BlobProperties properties = blobClient.getProperties();
                InputStream configStream = blobClient.downloadStream();
                Properties props = new Properties();
                props.load(configStream);
                System.out.println("Configuration loaded from Azure Blob Storage: "
                        + BLOB_CONTAINER_NAME + "/" + CONFIG_BLOB_NAME);
                configStream.close();
            } else {
                System.out.println("Warning: Configuration blob not found in Azure Blob Storage: "
                        + BLOB_CONTAINER_NAME + "/" + CONFIG_BLOB_NAME);
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration from Azure Blob Storage: "
                    + e.getMessage());
        }
    }

    private void initializeLogging() {
        try {
            // Cloud-ready: Initialize logging to Azure Blob Storage instead of local file system
            BlobClient blobClient = new BlobClientBuilder()
                    .connectionString(AZURE_STORAGE_CONNECTION_STRING)
                    .containerName(BLOB_CONTAINER_NAME)
                    .blobName(LOG_BLOB_NAME)
                    .buildClient();

            if (!blobClient.exists()) {
                // Create the log blob with initial content
                String initialLogContent = "Mini Java Application - Log Initialized\n";
                blobClient.upload(
                        new ByteArrayInputStream(initialLogContent.getBytes(StandardCharsets.UTF_8)),
                        initialLogContent.getBytes(StandardCharsets.UTF_8).length);
            }

            System.out.println("Logging initialized to Azure Blob Storage: "
                    + BLOB_CONTAINER_NAME + "/" + LOG_BLOB_NAME);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging to Azure Blob Storage: "
                    + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // Cloud-ready: Port from environment variable for dynamic port assignment
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
