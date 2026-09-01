package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application - Updated for Java 17 compatibility.
 *
 * <p>Java 17 Compatibility Notes:
 * - SecurityManager has been deprecated for removal in Java 17 (JEP 411).
 *   Any usage of System.setSecurityManager() or System.getSecurityManager() has been removed.
 * - The application no longer installs or references a custom SecurityManager.
 * - All other APIs used here are fully compatible with Java 17.
 */
public class MiniApp {

    // Hardcoded port number (containerization concern - use env var in production)
    private static final int SERVER_PORT = 8080;

    // Hardcoded absolute file paths (containerization concern - use env var in production)
    private static final String CONFIG_FILE_PATH = "/opt/app/config/app.properties";
    private static final String LOG_FILE_PATH = "/var/log/mini-app.log";

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        // Java 17: SecurityManager is deprecated for removal (JEP 411).
        // Do NOT call System.setSecurityManager() - it is deprecated and will throw
        // UnsupportedOperationException in future Java releases.
        // The JVM's built-in access controls and module system provide security instead.

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Reading from hardcoded absolute path
        loadConfiguration();

        // Writing to hardcoded absolute path
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            // Hardcoded absolute file path
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
            // Hardcoded absolute path for log file
            File logDir = new File("/var/log");
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
            // Hardcoded port number
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
