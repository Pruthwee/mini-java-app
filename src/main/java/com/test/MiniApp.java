package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.sun.net.httpserver.HttpServer;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8080"));
    
    // Port of the health check endpoint (must differ from SERVER_PORT)
    private static final int HEALTH_PORT = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8081"));
    
    // Paths come from the environment (set by the Kubernetes Deployment / ConfigMap)
    private static final String CONFIG_FILE_PATH = new File(System.getenv().getOrDefault("CONFIG_PATH", "/opt/app/config"), "app.properties").getPath();
    private static final String LOG_DIR_PATH = System.getenv().getOrDefault("FILE_STORAGE_PATH", System.getenv().getOrDefault("LOG_PATH", "/var/log"));
    private static final String LOG_FILE_PATH = new File(LOG_DIR_PATH, "mini-app.log").getPath();
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        startHealthServer();
        
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    // Health check endpoint: GET /health answers {"status":"UP"} from start-up for as long as the process runs.
    // The HTTP server's dispatcher thread also keeps the process running after the application's own work ends.
    private static void startHealthServer() {
        try {
            HttpServer health = HttpServer.create(new InetSocketAddress(HEALTH_PORT), 0);
            health.createContext("/health", exchange -> {
                byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            health.start();
            System.out.println("Health endpoint started on port: " + HEALTH_PORT + " (/health)");
        } catch (IOException e) {
            System.err.println("Failed to start health endpoint: " + e.getMessage());
        }
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
            // BLOCKER: Hardcoded absolute path for log file
            File logDir = new File(LOG_DIR_PATH);
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