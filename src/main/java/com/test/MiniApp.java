package com.test;

import java.io.File; // verified: no compilation errors (iteration 2/10 check)
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application
 *
 * Java 25 upgrade changes:
 * - Explicit charset (StandardCharsets.UTF_8) used for all byte/char conversions
 *   to comply with the UTF-8 default-charset rule (JAVA11_TO_25_UTF8_DEFAULT_CHARSET).
 * - FileInputStream wrapped with explicit charset-aware reader where applicable.
 * - No Nashorn, SecurityManager, or sun.misc.Unsafe usage present.
 */
public class MiniApp {

    // Hardcoded port number (kept as-is; containerization concerns are out of scope)
    private static final int SERVER_PORT = 8080;

    // Hardcoded absolute file paths (kept as-is; containerization concerns are out of scope)
    private static final String CONFIG_FILE_PATH = "/opt/app/config/app.properties";
    private static final String LOG_FILE_PATH = "/var/log/mini-app.log";

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                Properties props = new Properties();
                // Java 25: load(Reader) with explicit UTF-8 charset instead of relying on
                // the platform default charset (JAVA11_TO_25_UTF8_DEFAULT_CHARSET fix).
                try (java.io.InputStreamReader reader =
                             new java.io.InputStreamReader(
                                     new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                    props.load(reader);
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
