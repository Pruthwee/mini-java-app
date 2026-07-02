package com.test;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application - Cloud-ready version with Azure integration
 */
public class MiniApp {
    
    // Cloud-ready: Port from environment variable with fallback
    private static final int SERVER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("SERVER_PORT", "8080")
    );
    
    // Cloud-ready: Azure Blob Storage configuration from environment
    private static final String AZURE_STORAGE_CONNECTION_STRING = 
        System.getenv().getOrDefault("AZURE_STORAGE_CONNECTION_STRING", "");
    private static final String BLOB_CONTAINER_NAME = 
        System.getenv().getOrDefault("BLOB_CONTAINER_NAME", "app-config");
    private static final String CONFIG_BLOB_NAME = 
        System.getenv().getOrDefault("CONFIG_BLOB_NAME", "app.properties");
    private static final String LOG_BLOB_NAME = 
        System.getenv().getOrDefault("LOG_BLOB_NAME", "mini-app.log");
    
    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application (Cloud-Ready)...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Initialize Azure Blob Storage client
        initializeBlobStorage();
        
        // Load configuration from Azure Blob Storage
        loadConfiguration();
        
        // Initialize logging to Azure Blob Storage
        initializeLogging();
        
        // Initialize database connection with cloud-ready configuration
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void initializeBlobStorage() {
        try {
            if (AZURE_STORAGE_CONNECTION_STRING != null && !AZURE_STORAGE_CONNECTION_STRING.isEmpty()) {
                // Use connection string if provided
                blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(AZURE_STORAGE_CONNECTION_STRING)
                    .buildClient();
            } else {
                // Use Managed Identity for authentication (recommended for Azure)
                String storageAccountUrl = System.getenv().getOrDefault(
                    "AZURE_STORAGE_ACCOUNT_URL", 
                    "https://yourstorageaccount.blob.core.windows.net"
                );
                blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(storageAccountUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            }
            
            // Get or create container
            containerClient = blobServiceClient.getBlobContainerClient(BLOB_CONTAINER_NAME);
            if (!containerClient.exists()) {
                containerClient.create();
                System.out.println("Created blob container: " + BLOB_CONTAINER_NAME);
            }
            
            System.out.println("Azure Blob Storage initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Azure Blob Storage: " + e.getMessage());
            System.err.println("Application will continue with limited functionality");
        }
    }
    
    private void loadConfiguration() {
        try {
            if (containerClient != null) {
                BlobClient blobClient = containerClient.getBlobClient(CONFIG_BLOB_NAME);
                
                if (blobClient.exists()) {
                    // Download configuration from Azure Blob Storage
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    blobClient.download(outputStream);
                    
                    Properties props = new Properties();
                    props.load(new ByteArrayInputStream(outputStream.toByteArray()));
                    
                    System.out.println("Configuration loaded from Azure Blob Storage: " + CONFIG_BLOB_NAME);
                } else {
                    System.out.println("Warning: Configuration blob not found: " + CONFIG_BLOB_NAME);
                    System.out.println("Using default configuration from environment variables");
                }
            } else {
                System.out.println("Blob storage not available, using environment variables for configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            if (containerClient != null) {
                BlobClient logBlobClient = containerClient.getBlobClient(LOG_BLOB_NAME);
                
                // Create initial log entry in Azure Blob Storage
                String initialLogEntry = "Application started at: " + System.currentTimeMillis() + "\n";
                InputStream logStream = new ByteArrayInputStream(initialLogEntry.getBytes());
                
                logBlobClient.upload(logStream, initialLogEntry.length(), true);
                
                System.out.println("Logging initialized in Azure Blob Storage: " + LOG_BLOB_NAME);
            } else {
                System.out.println("Blob storage not available, using console logging");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // Cloud-ready: Port from environment variable
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT + " (from environment)");
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
