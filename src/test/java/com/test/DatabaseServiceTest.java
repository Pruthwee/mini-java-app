package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Tests cover connect(), disconnect(), executeQuery(), and private helper methods
 * via output verification and mocking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    @InjectMocks
    private DatabaseService databaseService;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        databaseService = new DatabaseService();
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
    void constructor_default_createsNonNullInstance() {
        // Arrange & Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("Multiple DatabaseService instances are independent")
    void constructor_multipleInstances_areIndependent() {
        // Arrange & Act
        DatabaseService service1 = new DatabaseService();
        DatabaseService service2 = new DatabaseService();

        // Assert
        assertNotNull(service1);
        assertNotNull(service2);
        assertNotSame(service1, service2, "Two instances should not be the same object");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints 'Connecting to database...' message")
    void connect_always_printsConnectingMessage() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act – connection will fail (no real DB), but the print happens first
        service.connect();

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Connecting to database..."),
                "Should print connecting message before attempting connection");
    }

    @Test
    @DisplayName("connect() prints error message when SQL connection fails")
    void connect_whenSQLExceptionThrown_printsErrorMessage() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.connect();   // No real DB → SQLException expected

        // Assert
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Database connection failed:"),
                "Should print error message on connection failure");
    }

    @Test
    @DisplayName("connect() with mocked DriverManager succeeds and prints success messages")
    void connect_withMockedDriverManager_printsSuccessMessages() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connected to database:"),
                    "Should print connected message on success");
            assertTrue(output.contains("Using username:"),
                    "Should print username on success");
        }
    }

    @Test
    @DisplayName("connect() initialises cache connection (prints Redis message)")
    void connect_withMockedDriverManager_printsRedisCacheMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connecting to Redis cache at:"),
                    "Should print Redis cache connection message");
        }
    }

    @Test
    @DisplayName("connect() initialises external services (prints API and payment URLs)")
    void connect_withMockedDriverManager_printsExternalServiceMessages() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Initializing external API:"),
                    "Should print external API initialization message");
            assertTrue(output.contains("Initializing payment service:"),
                    "Should print payment service initialization message");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() when connection is null does not throw exception")
    void disconnect_whenConnectionIsNull_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();  // connection is null by default

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() should not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() when connection is null produces no output")
    void disconnect_whenConnectionIsNull_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.disconnect();

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Database connection closed"),
                "Should not print close message when connection is null");
    }

    @Test
    @DisplayName("disconnect() with open mocked connection prints closed message")
    void disconnect_withOpenConnection_printsDatabaseConnectionClosed() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            outContent.reset();   // clear connect() output

            // Act
            service.disconnect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Database connection closed"),
                    "Should print 'Database connection closed' after successful disconnect");
        }
    }

    @Test
    @DisplayName("disconnect() with already-closed connection does not print closed message")
    void disconnect_withAlreadyClosedConnection_doesNotPrintClosedMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            outContent.reset();

            // Act
            service.disconnect();

            // Assert
            String output = outContent.toString();
            assertFalse(output.contains("Database connection closed"),
                    "Should not print closed message when connection is already closed");
        }
    }

    @Test
    @DisplayName("disconnect() when close() throws SQLException prints error message")
    void disconnect_whenCloseThrowsSQLException_printsErrorMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConnection).close();

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            errContent.reset();

            // Act
            service.disconnect();

            // Assert
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Failed to close database connection:"),
                    "Should print error message when close() throws SQLException");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() when connection is null does not throw exception")
    void executeQuery_whenConnectionIsNull_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();
        String sql = "SELECT 1";

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(sql),
                "executeQuery() should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() when connection is null produces no output")
    void executeQuery_whenConnectionIsNull_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.executeQuery("SELECT 1");

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Executing query:"),
                "Should not print query message when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with open connection prints executing query message")
    void executeQuery_withOpenConnection_printsExecutingQueryMessage() throws SQLException {
        // Arrange
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            outContent.reset();

            // Act
            service.executeQuery("SELECT * FROM users");

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Executing query: SELECT * FROM users"),
                    "Should print the SQL query being executed");
        }
    }

    @Test
    @DisplayName("executeQuery() sets query timeout to 30 seconds")
    void executeQuery_withOpenConnection_setsQueryTimeout() throws SQLException {
        // Arrange
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();

            // Act
            service.executeQuery("SELECT 1");

            // Assert
            verify(mockStmt).setQueryTimeout(30);
        }
    }

    @Test
    @DisplayName("executeQuery() calls execute() on the PreparedStatement")
    void executeQuery_withOpenConnection_callsExecuteOnStatement() throws SQLException {
        // Arrange
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();

            // Act
            service.executeQuery("DELETE FROM temp");

            // Assert
            verify(mockStmt).execute();
        }
    }

    @Test
    @DisplayName("executeQuery() when prepareStatement throws SQLException prints error message")
    void executeQuery_whenPrepareStatementThrowsSQLException_printsErrorMessage() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Prepare failed"));

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            errContent.reset();

            // Act
            service.executeQuery("INVALID SQL");

            // Assert
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Query execution failed:"),
                    "Should print error message when prepareStatement throws SQLException");
        }
    }

    @Test
    @DisplayName("executeQuery() with closed connection does not execute query")
    void executeQuery_withClosedConnection_doesNotExecuteQuery() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();
            service.connect();
            outContent.reset();

            // Act
            service.executeQuery("SELECT 1");

            // Assert
            String output = outContent.toString();
            assertFalse(output.contains("Executing query:"),
                    "Should not execute query when connection is closed");
        }
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw NullPointerException")
    void executeQuery_withNullSql_doesNotThrowNPE() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(null),
                "executeQuery() should handle null SQL gracefully when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw exception")
    void executeQuery_withEmptySql_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(""),
                "executeQuery() should handle empty SQL gracefully when connection is null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constant / Configuration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() uses expected DB URL containing localhost and port 3306")
    void connect_withMockedDriverManager_usesExpectedDbUrl() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert – verify the URL passed to DriverManager contains expected parts
            mockedDriverManager.verify(() ->
                    DriverManager.getConnection(
                            argThat(url -> url.contains("localhost") && url.contains("3306")),
                            anyString(),
                            anyString()));
        }
    }

    @Test
    @DisplayName("connect() uses 'root' as the database username")
    void connect_withMockedDriverManager_usesRootUsername() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert
            mockedDriverManager.verify(() ->
                    DriverManager.getConnection(anyString(), eq("root"), anyString()));
        }
    }

    @Test
    @DisplayName("connect() Redis message contains expected host 127.0.0.1 and port 6379")
    void connect_withMockedDriverManager_redisMessageContainsExpectedHostAndPort() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() ->
                    DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            DatabaseService service = new DatabaseService();

            // Act
            service.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("127.0.0.1"),
                    "Redis host should be 127.0.0.1");
            assertTrue(output.contains("6379"),
                    "Redis port should be 6379");
        }
    }
}
