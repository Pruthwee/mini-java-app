package com.test;

import com.azure.core.credential.TokenCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application updated for Azure cloud readiness.
 */
public class MiniApp {

    private final AzureConfigurationHelper configurationHelper = new AzureConfigurationHelper();
    private final AzureBlobStorageHelper blobStorageHelper = new AzureBlobStorageHelper(configurationHelper);

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
        String configBlobName = configurationHelper.getValue("APP_CONFIG_BLOB_NAME", "mini-app/app.properties");
        try {
            String content = blobStorageHelper.downloadBlobContent(configBlobName);
            if (content == null || content.isBlank()) {
                System.out.println("Warning: Configuration blob is empty or not found: " + configBlobName);
                return;
            }

            Properties props = new Properties();
            props.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            System.out.println("Configuration loaded from Azure Blob Storage blob: " + configBlobName);
        } catch (IOException e) {
            System.err.println("Failed to load configuration from Azure Blob Storage: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        String logBlobName = configurationHelper.getValue("APP_LOG_BLOB_NAME", "mini-app/logs/application.log");
        try {
            String startupMessage = "Logging initialized for cloud deployment\n";
            blobStorageHelper.uploadBlobContent(logBlobName, startupMessage);
            System.out.println("Logging initialized in Azure Blob Storage blob: " + logBlobName);
        } catch (RuntimeException e) {
            System.err.println("Failed to initialize logging in Azure Blob Storage: " + e.getMessage());
        }
    }

    private void startServer() {
        int serverPort = configurationHelper.getIntValue("SERVER_PORT", 8080);
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    private static final class AzureConfigurationHelper {
        private final ConfigurationClient configurationClient;

        private AzureConfigurationHelper() {
            String connectionString = System.getenv("AZURE_APP_CONFIGURATION_CONNECTION_STRING");
            if (connectionString != null && !connectionString.isBlank()) {
                this.configurationClient = new ConfigurationClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();
            } else {
                this.configurationClient = null;
            }
        }

        private String getValue(String key, String defaultValue) {
            String environmentValue = System.getenv(key);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue;
            }

            if (configurationClient != null) {
                try {
                    String value = configurationClient.getConfigurationSetting(key, null).getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                } catch (RuntimeException ignored) {
                    // Fall back to default value when Azure App Configuration is not reachable.
                }
            }
            return defaultValue;
        }

        private int getIntValue(String key, int defaultValue) {
            String value = getValue(key, String.valueOf(defaultValue));
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
    }

    private static final class AzureBlobStorageHelper {
        private final BlobContainerClient containerClient;

        private AzureBlobStorageHelper(AzureConfigurationHelper configurationHelper) {
            String connectionString = configurationHelper.getValue("AZURE_STORAGE_CONNECTION_STRING", "");
            String endpoint = configurationHelper.getValue("AZURE_STORAGE_BLOB_ENDPOINT", "");
            String containerName = configurationHelper.getValue("AZURE_STORAGE_CONTAINER", "mini-app-data");

            BlobContainerClientBuilder builder = new BlobContainerClientBuilder().containerName(containerName);
            if (!connectionString.isBlank()) {
                builder.connectionString(connectionString);
            } else if (!endpoint.isBlank()) {
                TokenCredential credential = new DefaultAzureCredentialBuilder().build();
                builder.endpoint(endpoint).credential(credential);
            } else {
                throw new IllegalStateException("Azure Blob Storage configuration is required via connection string or endpoint.");
            }

            this.containerClient = builder.buildClient();
            if (!this.containerClient.exists()) {
                this.containerClient.create();
            }
        }

        private String downloadBlobContent(String blobName) {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                return null;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }

        private void uploadBlobContent(String blobName, String content) {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            blobClient.upload(new ByteArrayInputStream(data), data.length, true);
        }
    }
}