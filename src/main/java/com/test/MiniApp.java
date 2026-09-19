package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application
 * Updated for Java 21 compatibility:
 * - Compiler source/target/release updated to 21 (in pom.xml)
 * - No javax.* imports present (Jakarta EE migration handled via Spring Boot 3.x)
 * - No SecurityManager usage (removed in Java 17+)
 * - No deprecated APIs from Java 11->17->21 path used
 * - FileInputStream properly closed via try-with-resources
 * - ServerSocket properly closed via try-with-resources
 */
public class MiniApp {

    // Port can be overridden via environment variable for containerization
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // File paths resolved from environment variables for containerization
    private static final String CONFIG_FILE_PATH = System.getenv().getOrDefault(
            "CONFIG_FILE_PATH", "/opt/app/config/app.properties");
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault(
            "LOG_FILE_PATH", "/var/log/mini-app.log");

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();

        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from a properties file.
     * Fixed: FileInputStream is now properly closed via try-with-resources (Java 21 best practice).
     */
    private void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                Properties props = new Properties();
                // Fixed: Use try-with-resources for FileInputStream to ensure it is always closed
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
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
            File logDir = new File(LOG_FILE_PATH).getParentFile();
            if (logDir != null && !logDir.exists()) {
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

    /**
     * Starts the server socket and listens for connections.
     * Fixed: ServerSocket is now properly closed via try-with-resources (Java 21 best practice).
     */
    private void startServer() {
        // Fixed: Use try-with-resources for ServerSocket to ensure it is always closed
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
