package com.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // cz-java-0061 FIX (line 15): Replaced hardcoded port 8080 with environment variable SERVER_PORT
    // to allow flexible container deployment and Helm chart parameterization on EKS.
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // FIX cz-java-0057: Replaced hardcoded absolute file paths with environment variables
    // backed by EFS-mounted PersistentVolumeClaim paths as defaults (EKS/EFS pattern)
    private static final String LOG_FILE_PATH = System.getenv("APP_LOG_FILE_PATH") != null
            ? System.getenv("APP_LOG_FILE_PATH")
            : "/mnt/efs/logs/mini-app.log";
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        // Load configuration from ConfigMap/Secret-backed environment variables
        loadConfiguration();
        
        // BLOCKER: Writing to hardcoded absolute path
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }
    
    private void loadConfiguration() {
        // FIX cz-java-0058: Externalized configuration loading from local filesystem
        // to Kubernetes ConfigMap/Secret-backed environment variables.
        // Configuration values are injected as environment variables (from ConfigMap/Secret
        // mounted via EKS), eliminating dependency on local filesystem structure.
        Properties props = new Properties();

        // Primary: load from ConfigMap/Secret volume-mounted path injected via APP_CONFIG_FILE_PATH env var
        // (e.g., /etc/config/app.properties mounted from a Kubernetes ConfigMap volume)
        String configPath = System.getenv("APP_CONFIG_FILE_PATH");
        if (configPath != null && !configPath.isEmpty()) {
            try (InputStream is = new java.io.FileInputStream(configPath)) {
                props.load(is);
                System.out.println("Configuration loaded from ConfigMap/Secret volume mount: " + configPath);
            } catch (IOException e) {
                System.err.println("Failed to load configuration from volume mount: " + e.getMessage());
            }
        } else {
            // Fallback: load individual config values from environment variables
            // (ConfigMap env-var injection pattern for EKS)
            System.getenv().forEach((key, value) -> {
                if (key.startsWith("APP_")) {
                    props.setProperty(key, value);
                }
            });
            System.out.println("Configuration loaded from environment variables (ConfigMap/Secret injection).");
        }
    }
    
    private void initializeLogging() {
        try {
            // FIX cz-java-0057: Replaced hardcoded "/var/log" with env var APP_LOG_DIR
            // defaulting to EFS-backed mount path /mnt/efs/logs
            String logDirPath = System.getenv("APP_LOG_DIR") != null
                    ? System.getenv("APP_LOG_DIR")
                    : "/mnt/efs/logs";
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
            // cz-java-0061 FIX (line 79): ServerSocket now uses SERVER_PORT resolved from
            // environment variable, enabling dynamic port assignment in container deployments.
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
