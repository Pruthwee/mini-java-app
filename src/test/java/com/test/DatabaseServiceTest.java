package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Tests cover: connect(), executeQuery(), disconnect() methods
 * and all internal helper methods via observable side effects.
 */
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private DatabaseService databaseService;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseService();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null DatabaseService instance")
    void constructor_defaultConstructor_createsInstance() {
        // Arrange & Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("Multiple instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        // Arrange & Act
        DatabaseService service1 = new DatabaseService();
        DatabaseService service2 = new DatabaseService();

        // Assert
        assertNotSame(service1, service2, "Two instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints connecting message before attempting connection")
    void connect_printsConnectingMessage() {
        // Act – connect() will fail to reach a real DB; that's fine, we only check the first print
        databaseService.connect();

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Connecting to PostgreSQL database"),
                "Should print connecting message");
    }

    @Test
    @DisplayName("connect() does not throw any exception when DB is unavailable")
    void connect_noExceptionPropagated_whenDbUnavailable() {
        // Act & Assert
        assertDoesNotThrow(() -> databaseService.connect(),
                "connect() should swallow exceptions and not propagate them");
    }

    @Test
    @DisplayName("connect() handles SQLException gracefully and prints error")
    void connect_sqlException_printsErrorMessage() {
        // Arrange – create the exception BEFORE opening MockedStatic to avoid
        // DriverManager.getLogWriter() being intercepted during SQLException construction
        SQLException sqlEx = new SQLException("Connection refused");
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            databaseService.connect();

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Connection refused") ||
                       errOutput.contains("database connection failed"),
                    "Should print SQL error message");
        }
    }

    @Test
    @DisplayName("connect() with successful connection prints success messages")
    void connect_successfulConnection_printsSuccessMessages() {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connected to PostgreSQL database"),
                    "Should print connected message");
            assertTrue(output.contains("Using username"),
                    "Should print username message");
            assertTrue(output.contains("Connecting to Redis cache"),
                    "Should print Redis cache connection message");
            assertTrue(output.contains("Initializing external API"),
                    "Should print external API initialization message");
            assertTrue(output.contains("Initializing payment service"),
                    "Should print payment service initialization message");
        }
    }

    @Test
    @DisplayName("connect() prints PostgreSQL JDBC URL containing jdbc:postgresql://")
    void connect_successfulConnection_urlContainsPostgresqlScheme() {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("jdbc:postgresql://"),
                    "DB URL should use jdbc:postgresql:// scheme (PostgreSQL migration)");
        }
    }

    @Test
    @DisplayName("connect() prints PostgreSQL port 5432")
    void connect_successfulConnection_urlContainsPostgresqlPort() {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("5432"),
                    "Should use PostgreSQL default port 5432");
        }
    }

    @Test
    @DisplayName("connect() prints postgres as username (PostgreSQL default)")
    void connect_successfulConnection_usernameIsPostgres() {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("postgres"),
                    "Username should be 'postgres' (PostgreSQL default)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() with null connection does nothing and does not throw")
    void executeQuery_nullConnection_doesNotThrow() {
        // Arrange – connection is null by default (no connect() called)
        // Act & Assert
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"),
                "executeQuery() should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with null connection produces no output")
    void executeQuery_nullConnection_producesNoOutput() {
        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Executing query"),
                "Should not print executing query when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with valid connection executes and prints query")
    void executeQuery_validConnection_executesQuery() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
        doNothing().when(mockStmt).setQueryTimeout(anyInt());
        when(mockStmt.execute()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.executeQuery("SELECT * FROM users");

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Executing query"),
                "Should print executing query message");
        assertTrue(output.contains("SELECT * FROM users"),
                "Should print the actual SQL query");
    }

    @Test
    @DisplayName("executeQuery() with closed connection does not execute query")
    void executeQuery_closedConnection_doesNotExecuteQuery() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Executing query"),
                "Should not execute query when connection is closed");
    }

    @Test
    @DisplayName("executeQuery() handles SQLException gracefully")
    void executeQuery_sqlException_printsErrorMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Syntax error in SQL"));

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act & Assert
        assertDoesNotThrow(() -> databaseService.executeQuery("INVALID SQL"),
                "executeQuery() should not propagate SQLException");

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Query execution failed") ||
                   errOutput.contains("Syntax error"),
                "Should print error message on SQLException");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw")
    void executeQuery_emptySql_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> databaseService.executeQuery(""),
                "executeQuery() should handle empty SQL gracefully");
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw")
    void executeQuery_nullSql_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> databaseService.executeQuery(null),
                "executeQuery() should handle null SQL gracefully");
    }

    @Test
    @DisplayName("executeQuery() sets query timeout to 30 seconds")
    void executeQuery_validConnection_setsQueryTimeout() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        verify(mockStmt).setQueryTimeout(30);
    }

    @Test
    @DisplayName("executeQuery() closes PreparedStatement after execution")
    void executeQuery_validConnection_closesStatement() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        verify(mockStmt).close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() with null connection does not throw")
    void disconnect_nullConnection_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() should not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() with null connection produces no output")
    void disconnect_nullConnection_producesNoOutput() {
        // Act
        databaseService.disconnect();

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("connection closed"),
                "Should not print closed message when connection is null");
    }

    @Test
    @DisplayName("disconnect() with open connection closes it and prints message")
    void disconnect_openConnection_closesAndPrintsMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.disconnect();

        // Assert
        verify(mockConnection).close();
        String output = outContent.toString();
        assertTrue(output.contains("connection closed"),
                "Should print connection closed message");
    }

    @Test
    @DisplayName("disconnect() with already-closed connection does not call close() again")
    void disconnect_alreadyClosedConnection_doesNotCallClose() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.disconnect();

        // Assert
        verify(mockConnection, never()).close();
    }

    @Test
    @DisplayName("disconnect() handles SQLException gracefully and prints error")
    void disconnect_sqlException_printsErrorMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConnection).close();

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() should not propagate SQLException");

        // Assert
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Failed to close") ||
                   errOutput.contains("Close failed"),
                "Should print error message on SQLException during disconnect");
    }

    @Test
    @DisplayName("disconnect() prints PostgreSQL in the closed message")
    void disconnect_openConnection_mentionsPostgreSQL() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.disconnect();

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("PostgreSQL"),
                "Disconnect message should mention PostgreSQL");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style Tests (connect → executeQuery → disconnect lifecycle)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: connect, executeQuery, disconnect works without exception")
    void lifecycle_connectExecuteDisconnect_noException() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act & Assert
            assertDoesNotThrow(() -> {
                databaseService.connect();
                databaseService.executeQuery("CREATE TABLE test (id INT)");
                databaseService.disconnect();
            }, "Full lifecycle should not throw any exception");
        }
    }

    @Test
    @DisplayName("executeQuery() called multiple times executes each query")
    void executeQuery_calledMultipleTimes_executesEachQuery() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);
            databaseService.connect();
        }

        // Act
        databaseService.executeQuery("SELECT 1");
        databaseService.executeQuery("SELECT 2");
        databaseService.executeQuery("SELECT 3");

        // Assert
        verify(mockConnection, times(3)).prepareStatement(anyString());
    }
}
