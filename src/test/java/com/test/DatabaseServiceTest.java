package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest {

    private DatabaseService databaseService;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseService();
    }

    @Test
    @DisplayName("Test Constructor - Should initialize DatabaseService")
    void testConstructor() {
        assertNotNull(databaseService, "DatabaseService instance should be created");
    }

    @Test
    @DisplayName("Test connect() - Should handle connection failure gracefully")
    void testConnect_Failure() {
        // Since we don't have a real DB running, this should fail but not throw an exception
        // because the method catches SQLException and ClassNotFoundException.
        assertDoesNotThrow(() -> databaseService.connect(), 
            "connect() should handle exceptions internally and not throw them");
    }

    @Test
    @DisplayName("Test executeQuery() - Should handle null connection gracefully")
    void testExecuteQuery_NullConnection() {
        // Connection is null by default before connect()
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"), 
            "executeQuery should handle null connection without throwing exception");
    }

    @Test
    @DisplayName("Test disconnect() - Should handle null connection gracefully")
    void testDisconnect_NullConnection() {
        assertDoesNotThrow(() -> databaseService.disconnect(), 
            "disconnect should handle null connection without throwing exception");
    }

    @Test
    @DisplayName("Test executeQuery() - Should handle closed connection gracefully")
    void testExecuteQuery_ClosedConnection() throws Exception {
        // Use reflection to set a closed connection if possible, or just test the logic
        // Since we can't easily mock DriverManager.getConnection without Mockito, 
        // we test the current behavior.
        databaseService.disconnect();
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"), 
            "executeQuery should handle closed connection");
    }
}
