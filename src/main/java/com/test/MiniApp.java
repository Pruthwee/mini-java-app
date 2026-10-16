package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // cz-java-0061: Server port externalized via SERVER_PORT environment variable
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // Externalized absolute file paths via environment variables (cz-java-0057)
    private static final String CONFIG_FILE_PATH = System.getenv().getOrDefault("APP_CONFIG_FILE_PATH", "/opt/app/config/app.properties");
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault("APP_LOG_FILE_PATH", "/var/log/mini-app.log");
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        // Start health check endpoint (mandatory containerization requirement)
        HealthCheckServer healthCheckServer = new HealthCheckServer();
        try {
            healthCheckServer.start();
        } catch (IOException e) {
            System.err.println("Warning: Failed to start health check server: " + e.getMessage());
        }

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Reading configuration from externalized path
        loadConfiguration();
        
        // Initializing logging from externalized path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // Path resolved from environment variable APP_CONFIG_FILE_PATH (cz-java-0057)
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(configFile));
                System.out.println("Configuration loaded from: " + CONFIG_FILE_PATH);
            } else {
                System.out.println("Warning: Configuration file not found at: " + CONFIG_FILE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // cz-java-0058: Log directory resolved from APP_LOG_DIRECTORY environment variable
            // backed by an EKS PersistentVolumeClaim (EFS) mount path instead of a hardcoded local path
            String logDirectory = System.getenv().getOrDefault("APP_LOG_DIRECTORY", "/mnt/efs/logs/mini-app");
            File logDir = new File(logDirectory);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            File logFile = new File(LOG_FILE_PATH);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            
            System.out.println("Logging initialized at: " + LOG_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            // cz-java-0061: Port resolved from SERVER_PORT environment variable
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
