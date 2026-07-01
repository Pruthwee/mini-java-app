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
    // Use environment variable for port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    // Use environment variables for file paths
    private static final String CONFIG_FILE_PATH = System.getenv().getOrDefault("CONFIG_FILE_PATH", "config/app.properties");
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault("LOG_FILE_PATH", "logs/mini-app.log");
    
    private DatabaseService dbService = new DatabaseService();

    public static void main(String[] args) {
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        loadConfiguration();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                System.out.println("Loading configuration from: " + CONFIG_FILE_PATH);
                // Simulate loading properties
            } else {
                System.out.println("Configuration file not found at " + CONFIG_FILE_PATH + ", using defaults.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            File logFile = new File(LOG_FILE_PATH);
            File logDir = logFile.getParentFile();
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs();
            }
            System.out.println("Logging initialized at: " + LOG_FILE_PATH);
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