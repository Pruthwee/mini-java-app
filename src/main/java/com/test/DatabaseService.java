package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service.
 *
 * Java 25 upgrade changes:
 * - Removed explicit Class.forName("com.mysql.cj.jdbc.Driver") call.
 *   Modern JDBC drivers (MySQL Connector/J 9.x) register themselves via the
 *   ServiceLoader mechanism (java.sql.Driver SPI), so the reflective
 *   Class.forName call is unnecessary and was a pattern flagged by the
 *   JAVA11_TO_25_STRONG_ENCAPSULATION rule (setAccessible / reflective
 *   class loading into JDK internals).
 * - mysql-connector-java (com.mysql:mysql-connector-j 9.1.0) replaces the
 *   deprecated mysql:mysql-connector-java 8.x artifact, which is incompatible
 *   with Java 25 (JAVA11_TO_25_DEPENDENCY_CONFLICTS /
 *   JAVA11_TO_25_SPRING_BOOT_COMPATIBILITY).
 */
public class DatabaseService {

    // Hardcoded database connection details
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "password123";

    // Hardcoded cache server details
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;

    // Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Java 25 fix (JAVA11_TO_25_STRONG_ENCAPSULATION):
            // Removed Class.forName("com.mysql.cj.jdbc.Driver") – the driver
            // self-registers via ServiceLoader; explicit reflective loading is
            // no longer needed and triggers strong-encapsulation warnings on
            // Java 25.
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            connectToCache();
            initializeExternalServices();

        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                stmt.setQueryTimeout(30);

                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
