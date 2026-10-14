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
 * Comprehensive JUnit 5 tests for DatabaseService.
 *
 * Covers:
 *  - connect()          – success path, SQLException path
 *  - executeQuery()     – open connection, closed connection, null connection, SQLException
 *  - disconnect()       – open connection, already-closed connection, null connection, SQLException
 *  - Private helpers    – connectToCache() and initializeExternalServices() are exercised
 *                         indirectly through connect().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    @InjectMocks
    private DatabaseService databaseService;

    private final ByteArrayOutputStream outContent  = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent  = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // -----------------------------------------------------------------------
    // connect()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("connect() – successful connection prints expected messages")
    void connect_successfulConnection_printsConnectedMessages() throws SQLException {
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Connecting to database..."),
                    "Should print 'Connecting to database...'");
            assertTrue(output.contains("Connected to database:"),
                    "Should print 'Connected to database:'");
            assertTrue(output.contains("Using username:"),
                    "Should print 'Using username:'");
            assertTrue(output.contains("Connecting to Redis cache at:"),
                    "Should print Redis cache connection message");
            assertTrue(output.contains("Initializing external API:"),
                    "Should print external API initialization message");
            assertTrue(output.contains("Initializing payment service:"),
                    "Should print payment service initialization message");
        }
    }

    @Test
    @DisplayName("connect() – SQLException is caught and error is printed to stderr")
    void connect_sqlException_printsErrorMessage() {
        // Build the exception BEFORE opening the MockedStatic scope so that
        // SQLException's constructor does not trigger Mockito's DriverManager stub.
        SQLException sqlEx = new SQLException("Connection refused");

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(sqlEx);

            // Should NOT throw – exception is caught internally
            assertDoesNotThrow(() -> databaseService.connect());

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Database connection failed:"),
                    "Should print error message to stderr");
            assertTrue(errOutput.contains("Connection refused"),
                    "Should include the original exception message");
        }
    }

    @Test
    @DisplayName("connect() – always prints 'Connecting to database...' before attempting connection")
    void connect_alwaysPrintsConnectingMessage() {
        // Build the exception BEFORE opening the MockedStatic scope.
        SQLException sqlEx = new SQLException("timeout");

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(sqlEx);

            databaseService.connect();

            assertTrue(outContent.toString().contains("Connecting to database..."));
        }
    }

    // -----------------------------------------------------------------------
    // executeQuery()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("executeQuery() – executes SQL when connection is open")
    void executeQuery_openConnection_executesSql() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset(); // clear connect() output

            databaseService.executeQuery("SELECT 1");

            verify(mockConnection).prepareStatement("SELECT 1");
            verify(mockStmt).setQueryTimeout(30);
            verify(mockStmt).execute();
            verify(mockStmt).close();

            assertTrue(outContent.toString().contains("Executing query: SELECT 1"));
        }
    }

    @Test
    @DisplayName("executeQuery() – does nothing when connection is null (no connect called)")
    void executeQuery_nullConnection_doesNothing() {
        // No connect() called → connection field is null
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"));
        // No output expected
        assertEquals("", outContent.toString().trim());
    }

    @Test
    @DisplayName("executeQuery() – does nothing when connection is closed")
    void executeQuery_closedConnection_doesNothing() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            databaseService.executeQuery("SELECT 1");

            verify(mockConnection, never()).prepareStatement(anyString());
        }
    }

    @Test
    @DisplayName("executeQuery() – catches SQLException and prints error to stderr")
    void executeQuery_sqlException_printsError() throws SQLException {
        Connection mockConnection = mock(Connection.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Syntax error"));

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            errContent.reset();

            assertDoesNotThrow(() -> databaseService.executeQuery("BAD SQL"));

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Query execution failed:"));
            assertTrue(errOutput.contains("Syntax error"));
        }
    }

    @Test
    @DisplayName("executeQuery() – handles empty SQL string without throwing")
    void executeQuery_emptySql_doesNotThrow() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement("")).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            assertDoesNotThrow(() -> databaseService.executeQuery(""));
        }
    }

    // -----------------------------------------------------------------------
    // disconnect()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("disconnect() – closes open connection and prints confirmation")
    void disconnect_openConnection_closesAndPrints() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            databaseService.disconnect();

            verify(mockConnection).close();
            assertTrue(outContent.toString().contains("Database connection closed"));
        }
    }

    @Test
    @DisplayName("disconnect() – does nothing when connection is null")
    void disconnect_nullConnection_doesNothing() {
        assertDoesNotThrow(() -> databaseService.disconnect());
        assertEquals("", outContent.toString().trim());
    }

    @Test
    @DisplayName("disconnect() – does nothing when connection is already closed")
    void disconnect_alreadyClosedConnection_doesNothing() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            databaseService.disconnect();

            verify(mockConnection, never()).close();
            assertEquals("", outContent.toString().trim());
        }
    }

    @Test
    @DisplayName("disconnect() – catches SQLException and prints error to stderr")
    void disconnect_sqlException_printsError() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConnection).close();

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            databaseService.connect();
            errContent.reset();

            assertDoesNotThrow(() -> databaseService.disconnect());

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Failed to close database connection:"));
            assertTrue(errOutput.contains("Close failed"));
        }
    }

    // -----------------------------------------------------------------------
    // Instantiation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DatabaseService – default constructor creates non-null instance")
    void constructor_defaultConstructor_createsInstance() {
        DatabaseService service = new DatabaseService();
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    // -----------------------------------------------------------------------
    // connect() + executeQuery() + disconnect() – full lifecycle
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Full lifecycle – connect, executeQuery, disconnect succeeds without errors")
    void fullLifecycle_connectExecuteDisconnect_noErrors() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConnection);

            assertDoesNotThrow(() -> {
                databaseService.connect();
                databaseService.executeQuery("INSERT INTO test VALUES (1)");
                databaseService.disconnect();
            });

            verify(mockStmt).execute();
            verify(mockConnection).close();
        }
    }
}
