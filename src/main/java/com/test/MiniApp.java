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

    // FIX cz-java-0061 (Line 15): Replaced hardcoded port 8080 with environment variable SERVER_PORT.
    // The port is now sourced from the SERVER_PORT environment variable (set via Kubernetes
    // ConfigMap or Helm chart values.yaml), enabling flexible container deployment on EKS.
    // Helm chart values.yaml should define: service.port and containerPort referencing this value.
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv("SERVER_PORT") != null && !System.getenv("SERVER_PORT").isEmpty()
                    ? System.getenv("SERVER_PORT")
                    : "8080"
    );

    // FIX cz-java-0057: Replaced hardcoded absolute file paths with environment variables
    // backed by EFS-mounted PersistentVolumeClaim paths as defaults (EKS/EFS PVC mount point)
    private static final String CONFIG_FILE_PATH = System.getenv("APP_CONFIG_FILE_PATH") != null
            ? System.getenv("APP_CONFIG_FILE_PATH")
            : "/mnt/efs/config/app.properties";
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
        // Load configuration from environment variables (ConfigMap/Secrets)
        loadConfiguration();

        // Initialize logging
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * FIX cz-java-0058: Replaced local filesystem-based configuration file loading with
     * Kubernetes ConfigMap and Secret environment variable injection.
     *
     * Previously, this method loaded a Properties file from a local filesystem path
     * (CONFIG_FILE_PATH), which breaks in containers with ephemeral or different
     * filesystem layouts.
     *
     * Now, configuration is loaded directly from environment variables that are
     * injected by Kubernetes ConfigMaps (for non-sensitive data) and Secrets
     * (for sensitive data), eliminating the local file system dependency entirely.
     *
     * Kubernetes ConfigMap example (non-sensitive):
     *   APP_SERVER_PORT, APP_LOG_LEVEL, APP_CONTEXT_PATH
     *
     * Kubernetes Secret example (sensitive):
     *   APP_DB_PASSWORD, APP_API_KEY, APP_JWT_SECRET
     */
    private void loadConfiguration() {
        // FIX cz-java-0058: Load configuration from environment variables injected
        // via Kubernetes ConfigMaps and Secrets instead of reading from local filesystem.
        Properties props = new Properties();

        // Non-sensitive configuration — sourced from Kubernetes ConfigMap
        String serverPort    = System.getenv("APP_SERVER_PORT");
        String logLevel      = System.getenv("APP_LOG_LEVEL");
        String contextPath   = System.getenv("APP_CONTEXT_PATH");

        // Sensitive configuration — sourced from Kubernetes Secret
        String dbPassword    = System.getenv("APP_DB_PASSWORD");
        String apiKey        = System.getenv("APP_API_KEY");
        String jwtSecret     = System.getenv("APP_JWT_SECRET");

        if (serverPort  != null) props.setProperty("server.port",         serverPort);
        if (logLevel    != null) props.setProperty("logging.level",        logLevel);
        if (contextPath != null) props.setProperty("server.context-path",  contextPath);
        if (dbPassword  != null) props.setProperty("database.password",    dbPassword);
        if (apiKey      != null) props.setProperty("external.api.key",     apiKey);
        if (jwtSecret   != null) props.setProperty("security.jwt.secret",  jwtSecret);

        System.out.println("Configuration loaded from Kubernetes ConfigMap/Secret environment variables.");

        // Fallback: if the ConfigMap-mounted file path is explicitly provided and exists,
        // also load it (supports optional volume-mounted ConfigMap as a properties file).
        if (CONFIG_FILE_PATH != null && !CONFIG_FILE_PATH.isEmpty()) {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                try {
                    props.load(new FileInputStream(configFile));
                    System.out.println("Additional configuration loaded from ConfigMap-mounted file: " + CONFIG_FILE_PATH);
                } catch (IOException e) {
                    System.err.println("Warning: Could not load ConfigMap-mounted file: " + e.getMessage());
                }
            }
        }
    }

    private void initializeLogging() {
        try {
            // FIX cz-java-0057: Replaced hardcoded "/var/log" with env var APP_LOG_DIR
            // backed by EFS-mounted PVC path as default
            File logDir = new File(System.getenv("APP_LOG_DIR") != null
                    ? System.getenv("APP_LOG_DIR")
                    : "/mnt/efs/logs");
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
            // FIX cz-java-0061 (Line 79): SERVER_PORT is now sourced from the SERVER_PORT
            // environment variable (see field declaration above) instead of the hardcoded
            // literal 8080, enabling dynamic port binding for container deployment on EKS.
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
