package com.test;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Mini Java Application updated for Azure cloud readiness.
 */
public class MiniApp {

    private static final String DEFAULT_SERVER_PORT = "8080";
    private static final String APP_CONFIG_ENDPOINT_ENV = "AZURE_APP_CONFIGURATION_ENDPOINT";
    private static final String APP_CONFIG_CONNECTION_STRING_ENV = "AZURE_APP_CONFIGURATION_CONNECTION_STRING";
    private static final String APP_CONFIG_KEY_ENV = "AZURE_APP_CONFIGURATION_KEY";
    private static final String APP_CONFIG_CONFIG_BLOB_ENV = "APP_CONFIG_BLOB_NAME";
    private static final String APP_LOG_BLOB_ENV = "APP_LOG_BLOB_NAME";

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
            BlobClient configBlobClient = AzureClients.createBlobClient(resolveConfigBlobName());
            if (configBlobClient.exists()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                configBlobClient.downloadStream(outputStream);
                Properties props = new Properties();
                props.load(new ByteArrayInputStream(outputStream.toByteArray()));
                System.out.println("Configuration loaded from Azure Blob Storage blob: " + configBlobClient.getBlobName());
                System.out.println("Loaded configuration entries: " + props.size());
                return;
            }

            Optional<String> appConfigContent = AzureClients.loadConfigurationValue(resolveAppConfigurationKey());
            if (appConfigContent.isPresent()) {
                Properties props = new Properties();
                props.load(new ByteArrayInputStream(appConfigContent.get().getBytes(StandardCharsets.UTF_8)));
                System.out.println("Configuration loaded from Azure App Configuration key: " + resolveAppConfigurationKey());
                System.out.println("Loaded configuration entries: " + props.size());
            } else {
                System.out.println("Warning: Configuration not found in Azure Blob Storage or Azure App Configuration.");
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        try {
            BlobContainerClient containerClient = AzureClients.createBlobContainerClient();
            String logBlobName = resolveLogBlobName();
            BlobClient logBlobClient = containerClient.getBlobClient(logBlobName);
            String startupLog = "Mini Java Application initialized in Azure environment\n";
            logBlobClient.upload(new ByteArrayInputStream(startupLog.getBytes(StandardCharsets.UTF_8)), startupLog.getBytes(StandardCharsets.UTF_8).length, true);
            System.out.println("Logging initialized in Azure Blob Storage blob: " + logBlobName);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        String configuredPort = AzureClients.loadConfigurationValue("server.port")
                .orElseGet(() -> System.getenv().getOrDefault("SERVER_PORT", DEFAULT_SERVER_PORT));
        int serverPort = Integer.parseInt(configuredPort);

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    private String resolveConfigBlobName() {
        return System.getenv().getOrDefault(APP_CONFIG_CONFIG_BLOB_ENV, "app.properties");
    }

    private String resolveLogBlobName() {
        return System.getenv().getOrDefault(APP_LOG_BLOB_ENV, "logs/mini-app.log");
    }

    private String resolveAppConfigurationKey() {
        return System.getenv().getOrDefault(APP_CONFIG_KEY_ENV, "miniapp.properties");
    }

    private static final class AzureClients {
        private static final String STORAGE_CONNECTION_STRING_ENV = "AZURE_STORAGE_CONNECTION_STRING";
        private static final String STORAGE_CONTAINER_ENV = "AZURE_STORAGE_CONTAINER_NAME";

        private AzureClients() {
        }

        static BlobContainerClient createBlobContainerClient() throws IOException {
            String connectionString = System.getenv(STORAGE_CONNECTION_STRING_ENV);
            String containerName = System.getenv().getOrDefault(STORAGE_CONTAINER_ENV, "mini-app-data");
            if (connectionString == null || connectionString.isBlank()) {
                throw new IOException("Missing environment variable: " + STORAGE_CONNECTION_STRING_ENV);
            }
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                    .connectionString(connectionString)
                    .containerName(containerName)
                    .buildClient();
            if (!containerClient.exists()) {
                containerClient.create();
            }
            return containerClient;
        }

        static BlobClient createBlobClient(String blobName) throws IOException {
            return createBlobContainerClient().getBlobClient(blobName);
        }

        static Optional<String> loadConfigurationValue(String key) {
            try {
                ConfigurationClient client = createConfigurationClient();
                if (client == null) {
                    return Optional.empty();
                }
                ConfigurationSetting setting = client.getConfigurationSetting(key, null);
                return Optional.ofNullable(setting.getValue());
            } catch (Exception ex) {
                return Optional.empty();
            }
        }

        private static ConfigurationClient createConfigurationClient() {
            String connectionString = System.getenv(APP_CONFIG_CONNECTION_STRING_ENV);
            if (connectionString != null && !connectionString.isBlank()) {
                return new ConfigurationClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();
            }

            String endpoint = System.getenv(APP_CONFIG_ENDPOINT_ENV);
            String credential = System.getenv("AZURE_APP_CONFIGURATION_KEY_CREDENTIAL");
            if (endpoint != null && !endpoint.isBlank() && credential != null && !credential.isBlank()) {
                return new ConfigurationClientBuilder()
                        .endpoint(endpoint)
                        .credential(new AzureKeyCredential(credential))
                        .buildClient();
            }
            return null;
        }
    }
}