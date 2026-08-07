package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // FIX cz-java-0061: Replaced hardcoded port number with environment-variable-driven
    // configuration.  SERVER_PORT is now resolved from the SERVER_PORT environment variable
    // (injected via Helm chart values.yaml / EKS Deployment manifest) so that the port
    // can be changed across Deployment, Service, Ingress, and health-probe resources
    // without recompiling the image.  Falls back to 8080 when the variable is absent.
    private static final int SERVER_PORT = resolvePort("SERVER_PORT", 8080);

    // FIX cz-java-0057: Replaced hardcoded absolute file paths with environment variables
    // backed by EFS-mounted PersistentVolumeClaim paths at standardized Linux mount points.
    private static final String CONFIG_FILE_PATH = System.getenv("APP_CONFIG_FILE_PATH") != null
            ? System.getenv("APP_CONFIG_FILE_PATH")
            : "/mnt/efs/app/config/app.properties";
    private static final String LOG_FILE_PATH = System.getenv("APP_LOG_FILE_PATH") != null
            ? System.getenv("APP_LOG_FILE_PATH")
            : "/mnt/efs/logs/mini-app.log";

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    // ---------------------------------------------------------------------------
    // Helper: read an integer environment variable; fall back to defaultPort if absent.
    // ---------------------------------------------------------------------------
    private static int resolvePort(String name, int defaultPort) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid value for env var " + name + ": " + value + ". Using default: " + defaultPort);
            }
        }
        return defaultPort;
    }
    
    private void initializeApplication() {
        // Load configuration from Kubernetes ConfigMap/Secret environment variables
        loadConfiguration();
        
        // Initialize logging path from environment variable
        initializeLogging();
        
        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        // FIX cz-java-0058: Replaced local filesystem-based configuration loading with
        // Kubernetes ConfigMap / Secret environment variable injection.
        // Configuration values are now read directly from environment variables,
        // which are populated by ConfigMaps (non-sensitive) and Secrets (sensitive)
        // mounted into the container by the EKS deployment manifest.
        Properties props = new Properties();

        // Non-sensitive config — sourced from a Kubernetes ConfigMap
        String appEnv   = System.getenv("APP_ENV");
        String logLevel = System.getenv("APP_LOG_LEVEL");
        String contextPath = System.getenv("APP_CONTEXT_PATH");

        if (appEnv != null)     props.setProperty("environment",        appEnv);
        if (logLevel != null)   props.setProperty("logging.level",      logLevel);
        if (contextPath != null) props.setProperty("server.context-path", contextPath);

        // Sensitive config — sourced from a Kubernetes Secret
        String dbUrl      = System.getenv("DB_URL");
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUrl != null)      props.setProperty("database.url",      dbUrl);
        if (dbUsername != null) props.setProperty("database.username", dbUsername);
        if (dbPassword != null) props.setProperty("database.password", dbPassword);

        System.out.println("Configuration loaded from Kubernetes ConfigMap/Secret environment variables.");
        System.out.println("Loaded " + props.size() + " configuration properties.");
    }

    private void initializeLogging() {
        try {
            // FIX cz-java-0057: Replaced hardcoded "/var/log" with parent directory derived
            // from LOG_FILE_PATH (resolved via env var APP_LOG_FILE_PATH or EFS mount default)
            java.io.File logDir = new java.io.File(LOG_FILE_PATH).getParentFile();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            java.io.File logFile = new java.io.File(LOG_FILE_PATH);
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
            // FIX cz-java-0061: SERVER_PORT is now resolved from the SERVER_PORT environment
            // variable (see field declaration above).  The Helm chart values.yaml centralises
            // the port value and propagates it to Deployment containerPort, Service port,
            // Ingress backend port, and liveness/readiness probe port so that all resources
            // remain consistent when the port is changed.
            //
            // BEFORE: ServerSocket serverSocket = new ServerSocket(8080);  // hardcoded
            // AFTER:  ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            //         where SERVER_PORT = resolvePort("SERVER_PORT", 8080)
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
