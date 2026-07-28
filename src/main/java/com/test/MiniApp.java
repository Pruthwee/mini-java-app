package com.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

public class MiniApp {
    
    // Externalized port number via environment variable for Azure App Configuration
    private static final int SERVER_PORT = System.getenv("SERVER_PORT") != null ? Integer.parseInt(System.getenv("SERVER_PORT")) : 8080;
    
    // Azure Blob Storage configuration from environment variables
    private static final String AZURE_STORAGE_CONNECTION_STRING = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    private static final String CONFIG_BLOB_NAME = "app.properties";
    private static final String LOG_BLOB_NAME = "mini-app.log";
    private static final String CONTAINER_NAME = "app-config";
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            if (AZURE_STORAGE_CONNECTION_STRING == null) {
                System.err.println("Azure Storage Connection String is not set.");
                return;
            }

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURE_STORAGE_CONNECTION_STRING)
                .buildClient();
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
            BlobClient blobClient = containerClient.getBlobClient(CONFIG_BLOB_NAME);

            if (blobClient.exists()) {
                Properties props = new Properties();
                try (InputStream is = blobClient.openInputStream()) {
                    props.load(is);
                }
                System.out.println("Configuration loaded from Azure Blob Storage: " + CONFIG_BLOB_NAME);
            } else {
                System.out.println("Warning: Configuration blob not found: " + CONFIG_BLOB_NAME);
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            if (AZURE_STORAGE_CONNECTION_STRING == null) {
                System.err.println("Azure Storage Connection String is not set.");
                return;
            }

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(AZURE_STORAGE_CONNECTION_STRING)
                .buildClient();
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
            BlobClient blobClient = containerClient.getBlobClient(LOG_BLOB_NAME);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write("Logging initialized at: ".getBytes());
            outputStream.write((LOG_BLOB_NAME).getBytes());
            
            blobClient.upload(new ByteArrayInputStream(outputStream.toByteArray()), outputStream.size(), true);
            
            System.out.println("Logging initialized in Azure Blob Storage: " + LOG_BLOB_NAME);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
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
