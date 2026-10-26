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
    
    // Port read from environment variable APP_PORT with fallback default
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8080"));
    
    // Paths read from environment variables with fallback defaults
    private static final String CONFIG_FILE_PATH = System.getenv().getOrDefault("CONFIG_PATH", "/app/config/app.properties");
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault("LOG_PATH", "/app/logs/mini-app.log");
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        
        // Start health check endpoint so the process stays alive for container probes
        HealthCheckServer healthServer = new HealthCheckServer();
        healthServer.start();
        
        app.startServer();
        
        // Keep the process running so the health endpoint stays available
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Application interrupted: " + e.getMessage());
            healthServer.stop();
        }
    }
    
    private void initializeApplication() {
        // BLOCKER: Reading from hardcoded absolute path
        loadConfiguration();
        
        // BLOCKER: Writing to hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            // BLOCKER: Hardcoded absolute file path
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
            // Log directory read from environment variable LOG_DIR with fallback default
            String logDirPath = System.getenv().getOrDefault("LOG_DIR", "/app/logs");
            File logDir = new File(logDirPath);
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
            // Port read from environment variable APP_PORT (see SERVER_PORT field)
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