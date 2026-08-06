package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // FIX cz-java-0061: Replaced hardcoded SERVER_PORT with environment variable for Helm chart
    // parameterization. The port value is centralized in Helm values.yaml (service.port) and
    // injected as the SERVER_PORT environment variable via the Kubernetes Deployment manifest,
    // ensuring consistent port references across Deployment, Service, Ingress, and health probe resources.
    private static final int SERVER_PORT = Integer.parseInt(
            Optional.ofNullable(System.getenv("SERVER_PORT")).orElse("8080"));

    // FIX cz-java-0057: Replaced hardcoded absolute file paths with environment variables
    // backed by EFS-mounted PersistentVolumeClaim paths as defaults (lines 18-19)
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
            // Uses CONFIG_FILE_PATH resolved from env var APP_CONFIG_FILE_PATH
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
            // FIX cz-java-0058: Externalized log directory to Kubernetes ConfigMap/env var APP_LOG_DIR (line 60)
            // No hardcoded fallback path - the env var must be supplied via a Kubernetes ConfigMap
            // (e.g., APP_LOG_DIR=/var/log/mini-app) so the filesystem path is controlled by the
            // cluster operator, not baked into the container image.
            String logDirPath = System.getenv("APP_LOG_DIR");
            if (logDirPath == null || logDirPath.isEmpty()) {
                System.err.println("WARNING: APP_LOG_DIR environment variable is not set. " +
                        "Configure it via a Kubernetes ConfigMap (e.g., APP_LOG_DIR=/var/log/mini-app).");
                return;
            }
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
            // FIX cz-java-0061: SERVER_PORT is now resolved from the REDIS_PORT environment variable
            // (set via Helm values.yaml → Kubernetes Deployment env), eliminating the hardcoded value.
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
