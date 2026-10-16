package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Covers connect(), executeQuery(), disconnect(), and private helper methods.
 */
@ExtendWith(MockitoExtension.class)
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
        DatabaseService service = new DatabaseService();
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("New instance has null connection field by default")
    void constructor_newInstance_connectionIsNull() throws Exception {
        DatabaseService service = new DatabaseService();
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        assertNull(connectionField.get(service),
                "Connection field should be null before connect() is called");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_HOST constant is 'localhost'")
    void staticField_dbHost_isLocalhost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_HOST");
        field.setAccessible(true);
        assertEquals("localhost", field.get(null));
    }

    @Test
    @DisplayName("DB_PORT constant is '3306'")
    void staticField_dbPort_is3306() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PORT");
        field.setAccessible(true);
        assertEquals("3306", field.get(null));
    }

    @Test
    @DisplayName("DB_NAME constant is 'mini_app_db'")
    void staticField_dbName_isMiniAppDb() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_NAME");
        field.setAccessible(true);
        assertEquals("mini_app_db", field.get(null));
    }

    @Test
    @DisplayName("DB_URL constant is correctly assembled")
    void staticField_dbUrl_isCorrectlyAssembled() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.startsWith("jdbc:mysql://"), "DB_URL should start with jdbc:mysql://");
        assertTrue(url.contains("localhost"), "DB_URL should contain localhost");
        assertTrue(url.contains("3306"), "DB_URL should contain port 3306");
        assertTrue(url.contains("mini_app_db"), "DB_URL should contain database name");
    }

    @Test
    @DisplayName("DB_USERNAME constant is 'root'")
    void staticField_dbUsername_isRoot() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_USERNAME");
        field.setAccessible(true);
        assertEquals("root", field.get(null));
    }

    @Test
    @DisplayName("DB_PASSWORD constant is 'password123'")
    void staticField_dbPassword_isPassword123() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PASSWORD");
        field.setAccessible(true);
        assertEquals("password123", field.get(null));
    }

    @Test
    @DisplayName("REDIS_HOST constant is '127.0.0.1'")
    void staticField_redisHost_is127001() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_HOST");
        field.setAccessible(true);
        assertEquals("127.0.0.1", field.get(null));
    }

    @Test
    @DisplayName("REDIS_PORT constant is 6379")
    void staticField_redisPort_is6379() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_PORT");
        field.setAccessible(true);
        assertEquals(6379, field.get(null));
    }

    @Test
    @DisplayName("EXTERNAL_API_URL constant is set correctly")
    void staticField_externalApiUrl_isSet() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertNotNull(url);
        assertTrue(url.contains("api.example.com"), "EXTERNAL_API_URL should contain api.example.com");
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant is set correctly")
    void staticField_paymentServiceUrl_isSet() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertNotNull(url);
        assertTrue(url.contains("payment"), "PAYMENT_SERVICE_URL should contain 'payment'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints 'Connecting to database...' message")
    void connect_printsConnectingMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            assertTrue(outContent.toString().contains("Connecting to database..."),
                    "Should print connecting message");
        }
    }

    @Test
    @DisplayName("connect() prints DB_URL in success message")
    void connect_onSuccess_printsDbUrl() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Connected to database:"),
                    "Should print 'Connected to database:' on success");
        }
    }

    @Test
    @DisplayName("connect() prints username in success message")
    void connect_onSuccess_printsUsername() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Using username:"),
                    "Should print 'Using username:' on success");
        }
    }

    @Test
    @DisplayName("connect() prints Redis cache connection message")
    void connect_onSuccess_printsRedisCacheMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Connecting to Redis cache at:"),
                    "Should print Redis cache connection message");
        }
    }

    @Test
    @DisplayName("connect() prints external API initialization message")
    void connect_onSuccess_printsExternalApiMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Initializing external API:"),
                    "Should print external API initialization message");
        }
    }

    @Test
    @DisplayName("connect() prints payment service initialization message")
    void connect_onSuccess_printsPaymentServiceMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Initializing payment service:"),
                    "Should print payment service initialization message");
        }
    }

    @Test
    @DisplayName("connect() on SQLException prints error message to stderr")
    void connect_onSQLException_printsErrorMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            SQLException sqlEx = new SQLException("Connection refused");
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            databaseService.connect();

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Database connection failed:"),
                    "Should print error message on SQLException");
            assertTrue(errOutput.contains("Connection refused"),
                    "Should include the original exception message");
        }
    }

    @Test
    @DisplayName("connect() on SQLException does NOT set connection field")
    void connect_onSQLException_connectionRemainsNull() throws Exception {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            SQLException sqlEx = new SQLException("Connection refused");
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            databaseService.connect();

            Field connectionField = DatabaseService.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            assertNull(connectionField.get(databaseService),
                    "Connection should remain null after failed connect()");
        }
    }

    @Test
    @DisplayName("connect() on success sets connection field to non-null")
    void connect_onSuccess_setsConnectionField() throws Exception {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            Field connectionField = DatabaseService.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            assertNotNull(connectionField.get(databaseService),
                    "Connection should be set after successful connect()");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() with null connection does nothing (no exception)")
    void executeQuery_withNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"),
                "executeQuery() should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with null connection produces no output")
    void executeQuery_withNullConnection_producesNoOutput() {
        databaseService.executeQuery("SELECT 1");
        assertEquals("", outContent.toString(),
                "No output expected when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with open connection prints executing message")
    void executeQuery_withOpenConnection_printsExecutingMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT * FROM users");

        assertTrue(outContent.toString().contains("Executing query: SELECT * FROM users"),
                "Should print executing query message");
    }

    @Test
    @DisplayName("executeQuery() with open connection sets query timeout to 30")
    void executeQuery_withOpenConnection_setsQueryTimeout() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockStmt).setQueryTimeout(30);
    }

    @Test
    @DisplayName("executeQuery() with open connection calls stmt.execute()")
    void executeQuery_withOpenConnection_callsExecute() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("DELETE FROM temp");

        verify(mockStmt).execute();
    }

    @Test
    @DisplayName("executeQuery() with open connection calls stmt.close()")
    void executeQuery_withOpenConnection_closesStatement() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockStmt).close();
    }

    @Test
    @DisplayName("executeQuery() with closed connection does nothing")
    void executeQuery_withClosedConnection_doesNothing() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        assertEquals("", outContent.toString(),
                "No output expected when connection is closed");
    }

    @Test
    @DisplayName("executeQuery() on SQLException prints error to stderr")
    void executeQuery_onSQLException_printsError() throws Exception {
        Connection mockConn = mock(Connection.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenThrow(new SQLException("Syntax error"));

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("INVALID SQL");

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Query execution failed:"),
                "Should print query execution failed message");
        assertTrue(errOutput.contains("Syntax error"),
                "Should include original exception message");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw")
    void executeQuery_withEmptySql_doesNotThrow() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery(""),
                "executeQuery() should not throw for empty SQL string");
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw (delegates to driver)")
    void executeQuery_withNullSql_doesNotThrow() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(null)).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery(null),
                "executeQuery() should not propagate exceptions");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() with null connection does not throw")
    void disconnect_withNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() should not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() with null connection produces no output")
    void disconnect_withNullConnection_producesNoOutput() {
        databaseService.disconnect();
        assertEquals("", outContent.toString(),
                "No output expected when connection is null");
    }

    @Test
    @DisplayName("disconnect() with open connection calls connection.close()")
    void disconnect_withOpenConnection_closesConnection() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        verify(mockConn).close();
    }

    @Test
    @DisplayName("disconnect() with open connection prints closed message")
    void disconnect_withOpenConnection_printsClosed() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        assertTrue(outContent.toString().contains("Database connection closed"),
                "Should print 'Database connection closed'");
    }

    @Test
    @DisplayName("disconnect() with already-closed connection does not call close()")
    void disconnect_withClosedConnection_doesNotCallClose() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        verify(mockConn, never()).close();
    }

    @Test
    @DisplayName("disconnect() on SQLException prints error to stderr")
    void disconnect_onSQLException_printsError() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConn).close();

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Failed to close database connection:"),
                "Should print failed to close message");
        assertTrue(errOutput.contains("Close failed"),
                "Should include original exception message");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Method Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connectToCache() private method prints Redis host and port")
    void connectToCache_printsRedisHostAndPort() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("127.0.0.1"),
                "connectToCache() should print Redis host 127.0.0.1");
        assertTrue(output.contains("6379"),
                "connectToCache() should print Redis port 6379");
    }

    @Test
    @DisplayName("initializeExternalServices() private method prints external API URL")
    void initializeExternalServices_printsExternalApiUrl() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("Initializing external API:"),
                "initializeExternalServices() should print external API message");
    }

    @Test
    @DisplayName("initializeExternalServices() private method prints payment service URL")
    void initializeExternalServices_printsPaymentServiceUrl() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("Initializing payment service:"),
                "initializeExternalServices() should print payment service message");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: connect, executeQuery, disconnect with mocked connection")
    void fullLifecycle_connectExecuteDisconnect_succeeds() throws Exception {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);

            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
            when(mockStmt.execute()).thenReturn(true);

            databaseService.connect();
            databaseService.executeQuery("SELECT 1");
            databaseService.disconnect();

            verify(mockConn).prepareStatement("SELECT 1");
            verify(mockStmt).execute();
            verify(mockConn).close();
        }
    }

    @Test
    @DisplayName("Multiple executeQuery() calls on same connection all succeed")
    void executeQuery_multipleCallsSameConnection_allSucceed() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");
        databaseService.executeQuery("SELECT 2");
        databaseService.executeQuery("SELECT 3");

        verify(mockStmt, times(3)).execute();
        verify(mockStmt, times(3)).close();
    }

    @Test
    @DisplayName("disconnect() called twice on same connection only closes once")
    void disconnect_calledTwice_closesOnlyOnce() throws Exception {
        Connection mockConn = mock(Connection.class);
        // First call: open; second call: already closed
        when(mockConn.isClosed()).thenReturn(false).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();
        databaseService.disconnect();

        verify(mockConn, times(1)).close();
    }
}
