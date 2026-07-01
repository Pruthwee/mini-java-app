package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;

public class MiniApp {
    private final int serverPort;
    private final String configFilePath;
    private final String logFilePath;

    public MiniApp() {
        this.serverPort = Integer.parseInt(Optional.ofNullable(System.getenv("SERVER_PORT")).orElse("8080"));
        this.configFilePath = Optional.ofNullable(System.getenv("CONFIG_FILE_PATH")).orElse("config/app.properties");
        this.logFilePath = Optional.ofNullable(System.getenv("LOG_FILE_PATH")).orElse("logs/mini-app.log");
    }

    public static void main(String[] args) {
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }
    
    private void initializeApplication() {
        try {
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                fis.close();
                System.out.println("Configuration loaded from: " + configFilePath);
            } else {
                System.out.println("Warning: Configuration file not found at: " + configFilePath);
            }
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            File logFile = new File(logFilePath);
            File logDir = logFile.getParentFile();
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs();
            }
            System.out.println("Logging initialized at: " + logFilePath);
            
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Server started on port: " + serverPort);
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Server interrupted: " + e.getMessage());
        }
    }
}
