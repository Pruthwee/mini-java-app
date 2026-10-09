package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 *
 * Java 21 upgrade changes applied:
 * - JAVA11_TO_21_UTF8_DEFAULT_CHARSET: Explicit charset usage via StandardCharsets.UTF_8
 * - JAVA11_TO_21_DEPENDENCY_UPDATES: Source/target updated to Java 21 in pom.xml
 */
public class MiniApp {

    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = 8080;

    // BLOCKER: Hardcoded absolute file path
    private static final String CONFIG_FILE_PATH = "/opt/app/config/app.properties";
    private static final String LOG_FILE_PATH = "/var/log/mini-app.log";

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
            // BLOCKER: Hardcoded absolute file path
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                Properties props = new Properties();
                // JAVA11_TO_21_UTF8_DEFAULT_CHARSET: Use explicit UTF-8 charset instead of
                // relying on platform default (which changed to UTF-8 in Java 18 but was
                // OS-locale-dependent in Java 11).
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(new java.io.InputStreamReader(fis, StandardCharsets.UTF_8));
                }
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
            // BLOCKER: Hardcoded absolute path for log file
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
            // BLOCKER: Hardcoded port number
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
