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
    
    // FIX cz-java-0061 (Line 15): Externalized SERVER_PORT to environment variable for Helm chart
    // parameterization. The port value is supplied via Helm values.yaml (service.port) and injected
    // as SERVER_PORT env var, enabling consistent port references across Deployment, Service,
    // Ingress, and health probe resources on EKS.
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv("SERVER_PORT") != null ? System.getenv("SERVER_PORT") : "8080");
    
    // FIX cz-java-0057 (Line 18): Replaced hardcoded absolute path with EFS-backed env var (APP_CONFIG_FILE_PATH)
    private static final String CONFIG_FILE_PATH = System.getenv("APP_CONFIG_FILE_PATH") != null ? System.getenv("APP_CONFIG_FILE_PATH") : "/mnt/efs/app/config/app.properties";
    // FIX cz-java-0057 (Line 19): Replaced hardcoded absolute path with EFS-backed env var (APP_LOG_FILE_PATH)
    private static final String LOG_FILE_PATH = System.getenv("APP_LOG_FILE_PATH") != null ? System.getenv("APP_LOG_FILE_PATH") : "/mnt/efs/logs/mini-app.log";
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
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
            // Uses CONFIG_FILE_PATH which is now resolved from env var APP_CONFIG_FILE_PATH
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
            // FIX cz-java-0058 (Line 60): Externalized log directory to Kubernetes ConfigMap-injected env var (APP_LOG_DIR).
            // No hardcoded local filesystem path fallback — path must be supplied via a Kubernetes ConfigMap
            // volume mount or environment variable injection at runtime.
            String logDirPath = System.getenv("APP_LOG_DIR");
            if (logDirPath == null || logDirPath.isEmpty()) {
                System.err.println("WARNING: APP_LOG_DIR environment variable is not set. " +
                        "Configure it via a Kubernetes ConfigMap volume mount or env injection.");
            } else {
                File logDir = new File(logDirPath);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
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
            // FIX cz-java-0061 (Line 79): ServerSocket now binds to the env-var-driven SERVER_PORT
            // constant (resolved above). The Helm chart values.yaml (service.port) is the single
            // source of truth for the port, ensuring consistent references across Deployment,
            // Service, Ingress, and health probe resources on EKS.
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
