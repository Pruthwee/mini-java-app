package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService.
 *
 * Strategy:
 *  - connect()        → tested via output capture (real DB not available; SQLException path covered)
 *  - executeQuery()   → tested with a mocked Connection / PreparedStatement
 *  - disconnect()     → tested with a mocked Connection
 *  - constant fields  → verified via reflection
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private DatabaseService databaseService;

    private final ByteArrayOutputStream outContent  = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent  = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor creates a non-null instance")
    void constructor_defaultConstructor_createsInstance() {
        // Arrange / Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    // -----------------------------------------------------------------------
    // connect() – no real DB; we verify the error path and output messages
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("connect() prints 'Connecting to database...' before attempting connection")
    void connect_printsConnectingMessage() {
        // Act – will fail with SQLException because no real DB is present
        databaseService.connect();

        // Assert
        String output = outContent.toString() + errContent.toString();
        assertTrue(output.contains("Connecting to database..."),
                "Should print connecting message");
    }

    @Test
    @DisplayName("connect() prints error message when database is unavailable")
    void connect_whenDatabaseUnavailable_printsErrorMessage() {
        // Act
        databaseService.connect();

        // Assert – no real DB → SQLException → error printed to stderr
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Database connection failed:"),
                "Should print database connection failed message on stderr");
    }

    @Test
    @DisplayName("connect() does not throw any unchecked exception when DB is unavailable")
    void connect_whenDatabaseUnavailable_doesNotThrow() {
        // Act & Assert – exception must be swallowed internally
        assertDoesNotThrow(() -> databaseService.connect(),
                "connect() should handle SQLException internally without propagating");
    }

    // -----------------------------------------------------------------------
    // executeQuery() – uses a mocked Connection + PreparedStatement
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("executeQuery() executes SQL when connection is open")
    void executeQuery_withOpenConnection_executesSql() throws Exception {
        // Arrange
        Connection mockConnection       = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);

        injectConnection(databaseService, mockConnection);

        String sql = "SELECT 1";

        // Act
        databaseService.executeQuery(sql);

        // Assert
        verify(mockConnection).prepareStatement(sql);
        verify(mockStatement).setQueryTimeout(30);
        verify(mockStatement).execute();
        verify(mockStatement).close();
    }

    @Test
    @DisplayName("executeQuery() prints executing query message")
    void executeQuery_withOpenConnection_printsExecutingMessage() throws Exception {
        // Arrange
        Connection mockConnection       = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);

        injectConnection(databaseService, mockConnection);

        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        assertTrue(outContent.toString().contains("Executing query:"),
                "Should print 'Executing query:' message");
    }

    @Test
    @DisplayName("executeQuery() does nothing when connection is null")
    void executeQuery_withNullConnection_doesNothing() throws Exception {
        // Arrange – connection field stays null (default)
        injectConnection(databaseService, null);

        // Act & Assert – no exception, no output
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"));
        assertEquals("", outContent.toString().trim(),
                "No output expected when connection is null");
    }

    @Test
    @DisplayName("executeQuery() does nothing when connection is closed")
    void executeQuery_withClosedConnection_doesNothing() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);
        injectConnection(databaseService, mockConnection);

        // Act
        databaseService.executeQuery("SELECT 1");

        // Assert
        verify(mockConnection, never()).prepareStatement(anyString());
    }

    @Test
    @DisplayName("executeQuery() handles SQLException gracefully")
    void executeQuery_whenSQLExceptionThrown_printsError() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Simulated SQL error"));
        injectConnection(databaseService, mockConnection);

        // Act
        assertDoesNotThrow(() -> databaseService.executeQuery("BAD SQL"));

        // Assert
        assertTrue(errContent.toString().contains("Query execution failed:"),
                "Should print query execution failed message");
    }

    @Test
    @DisplayName("executeQuery() does not throw when SQL string is empty")
    void executeQuery_withEmptySql_doesNotThrow() throws Exception {
        // Arrange
        Connection mockConnection       = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockConnection.isClosed()).thenReturn(false);
        when(mockConnection.prepareStatement("")).thenReturn(mockStatement);
        injectConnection(databaseService, mockConnection);

        // Act & Assert
        assertDoesNotThrow(() -> databaseService.executeQuery(""));
    }

    // -----------------------------------------------------------------------
    // disconnect()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("disconnect() closes an open connection")
    void disconnect_withOpenConnection_closesConnection() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        injectConnection(databaseService, mockConnection);

        // Act
        databaseService.disconnect();

        // Assert
        verify(mockConnection).close();
    }

    @Test
    @DisplayName("disconnect() prints 'Database connection closed' after closing")
    void disconnect_withOpenConnection_printsClosed() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        injectConnection(databaseService, mockConnection);

        // Act
        databaseService.disconnect();

        // Assert
        assertTrue(outContent.toString().contains("Database connection closed"),
                "Should print connection closed message");
    }

    @Test
    @DisplayName("disconnect() does nothing when connection is null")
    void disconnect_withNullConnection_doesNothing() throws Exception {
        // Arrange
        injectConnection(databaseService, null);

        // Act & Assert
        assertDoesNotThrow(() -> databaseService.disconnect());
        assertEquals("", outContent.toString().trim(),
                "No output expected when connection is null");
    }

    @Test
    @DisplayName("disconnect() does nothing when connection is already closed")
    void disconnect_withAlreadyClosedConnection_doesNothing() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);
        injectConnection(databaseService, mockConnection);

        // Act
        databaseService.disconnect();

        // Assert
        verify(mockConnection, never()).close();
    }

    @Test
    @DisplayName("disconnect() handles SQLException gracefully")
    void disconnect_whenSQLExceptionThrown_printsError() throws Exception {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConnection).close();
        injectConnection(databaseService, mockConnection);

        // Act
        assertDoesNotThrow(() -> databaseService.disconnect());

        // Assert
        assertTrue(errContent.toString().contains("Failed to close database connection:"),
                "Should print failed to close message");
    }

    // -----------------------------------------------------------------------
    // Static constant field verification via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DB_URL constant contains expected JDBC MySQL prefix")
    void constants_dbUrl_containsJdbcMysql() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String dbUrl = (String) field.get(null);
        assertTrue(dbUrl.startsWith("jdbc:mysql://"),
                "DB_URL should start with 'jdbc:mysql://'");
    }

    @Test
    @DisplayName("DB_HOST constant is 'localhost'")
    void constants_dbHost_isLocalhost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_HOST");
        field.setAccessible(true);
        assertEquals("localhost", field.get(null));
    }

    @Test
    @DisplayName("DB_PORT constant is '3306'")
    void constants_dbPort_is3306() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PORT");
        field.setAccessible(true);
        assertEquals("3306", field.get(null));
    }

    @Test
    @DisplayName("DB_NAME constant is 'mini_app_db'")
    void constants_dbName_isMiniAppDb() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_NAME");
        field.setAccessible(true);
        assertEquals("mini_app_db", field.get(null));
    }

    @Test
    @DisplayName("REDIS_PORT constant is 6379")
    void constants_redisPort_is6379() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_PORT");
        field.setAccessible(true);
        assertEquals(6379, field.get(null));
    }

    @Test
    @DisplayName("REDIS_HOST constant is '127.0.0.1'")
    void constants_redisHost_isLoopback() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_HOST");
        field.setAccessible(true);
        assertEquals("127.0.0.1", field.get(null));
    }

    @Test
    @DisplayName("EXTERNAL_API_URL constant contains expected host")
    void constants_externalApiUrl_containsExpectedHost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.contains("api.example.com"),
                "EXTERNAL_API_URL should contain 'api.example.com'");
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant uses HTTPS")
    void constants_paymentServiceUrl_usesHttps() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.startsWith("https://"),
                "PAYMENT_SERVICE_URL should use HTTPS");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Injects a {@link Connection} into the private {@code connection} field of
     * the given {@link DatabaseService} instance using reflection.
     */
    private void injectConnection(DatabaseService service, Connection connection) throws Exception {
        Field field = DatabaseService.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(service, connection);
    }
}
