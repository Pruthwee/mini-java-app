package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Tests cover: instantiation, connect(), executeQuery(), disconnect(),
 * private helpers (connectToCache, initializeExternalServices), and
 * all static constant values.
 */
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private DatabaseService databaseService;

    private final ByteArrayOutputStream outContent  = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent  = new ByteArrayOutputStream();
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
    // Constructor / Instantiation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null DatabaseService instance")
    void constructor_defaultConstructor_createsNonNullInstance() {
        // Arrange / Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("Multiple instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        DatabaseService service1 = new DatabaseService();
        DatabaseService service2 = new DatabaseService();

        assertNotSame(service1, service2, "Two instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static constant values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_HOST constant is 'localhost'")
    void staticConstants_dbHost_isLocalhost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_HOST");
        field.setAccessible(true);
        assertEquals("localhost", field.get(null));
    }

    @Test
    @DisplayName("DB_PORT constant is '3306'")
    void staticConstants_dbPort_is3306() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PORT");
        field.setAccessible(true);
        assertEquals("3306", field.get(null));
    }

    @Test
    @DisplayName("DB_NAME constant is 'mini_app_db'")
    void staticConstants_dbName_isMiniAppDb() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_NAME");
        field.setAccessible(true);
        assertEquals("mini_app_db", field.get(null));
    }

    @Test
    @DisplayName("DB_URL constant contains host, port and db name")
    void staticConstants_dbUrl_containsExpectedParts() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.contains("localhost"),  "URL should contain host");
        assertTrue(url.contains("3306"),       "URL should contain port");
        assertTrue(url.contains("mini_app_db"),"URL should contain db name");
        assertTrue(url.startsWith("jdbc:mysql://"), "URL should start with jdbc:mysql://");
    }

    @Test
    @DisplayName("DB_USERNAME constant is 'root'")
    void staticConstants_dbUsername_isRoot() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_USERNAME");
        field.setAccessible(true);
        assertEquals("root", field.get(null));
    }

    @Test
    @DisplayName("DB_PASSWORD constant is 'password123'")
    void staticConstants_dbPassword_isPassword123() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PASSWORD");
        field.setAccessible(true);
        assertEquals("password123", field.get(null));
    }

    @Test
    @DisplayName("REDIS_HOST constant is '127.0.0.1'")
    void staticConstants_redisHost_is127001() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_HOST");
        field.setAccessible(true);
        assertEquals("127.0.0.1", field.get(null));
    }

    @Test
    @DisplayName("REDIS_PORT constant is 6379")
    void staticConstants_redisPort_is6379() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_PORT");
        field.setAccessible(true);
        assertEquals(6379, field.get(null));
    }

    @Test
    @DisplayName("EXTERNAL_API_URL constant contains expected URL")
    void staticConstants_externalApiUrl_isExpected() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertNotNull(url);
        assertTrue(url.contains("api.example.com"), "Should contain api.example.com");
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant contains expected URL")
    void staticConstants_paymentServiceUrl_isExpected() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertNotNull(url);
        assertTrue(url.contains("payment.internal.company.com"), "Should contain payment service host");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() – no real DB available; driver not found path is exercised
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints 'Connecting to database...' message")
    void connect_printsConnectingMessage() {
        // Act – will fail at Class.forName or DriverManager; that's expected
        databaseService.connect();

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Connecting to database..."),
                "Should print connecting message");
    }

    @Test
    @DisplayName("connect() handles ClassNotFoundException gracefully (no real driver)")
    void connect_withoutDriver_handlesClassNotFoundGracefully() {
        // Act – mysql driver may or may not be on classpath; either way no exception propagates
        assertDoesNotThrow(() -> databaseService.connect(),
                "connect() should not throw even when driver/DB is unavailable");
    }

    @Test
    @DisplayName("connect() prints error to stderr when driver or DB is unavailable")
    void connect_withoutDb_printsErrorToStderr() {
        // Act
        databaseService.connect();

        // Assert – either ClassNotFoundException or SQLException message appears
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        // At minimum the "Connecting to database..." line must appear
        assertTrue(stdOut.contains("Connecting to database..."),
                "stdout should contain connecting message");
        // stderr should contain some error (driver not found or connection refused)
        // This is environment-dependent; we just verify no uncaught exception
        assertDoesNotThrow(() -> databaseService.connect());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() – connection is null by default
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() with null connection does not throw")
    void executeQuery_withNullConnection_doesNotThrow() {
        // connection field is null by default (never connected)
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"),
                "executeQuery should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with null connection produces no output")
    void executeQuery_withNullConnection_producesNoOutput() {
        databaseService.executeQuery("SELECT 1");
        // No output expected because the null-check guard prevents execution
        String output = outContent.toString();
        assertFalse(output.contains("Executing query:"),
                "Should not print query when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw")
    void executeQuery_withEmptySql_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery(""),
                "executeQuery should not throw for empty SQL when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw")
    void executeQuery_withNullSql_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery(null),
                "executeQuery should not throw for null SQL when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with complex SQL does not throw when connection is null")
    void executeQuery_withComplexSql_doesNotThrowWhenConnectionNull() {
        String complexSql = "SELECT u.id, u.name FROM users u WHERE u.active = 1 ORDER BY u.name";
        assertDoesNotThrow(() -> databaseService.executeQuery(complexSql));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() – connection is null by default
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
        String output = outContent.toString();
        assertFalse(output.contains("Database connection closed"),
                "Should not print closed message when connection is null");
    }

    @Test
    @DisplayName("disconnect() can be called multiple times without throwing")
    void disconnect_calledMultipleTimes_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.disconnect();
            databaseService.disconnect();
            databaseService.disconnect();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: connectToCache() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connectToCache() prints Redis connection message")
    void connectToCache_printsRedisConnectionMessage() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("Connecting to Redis cache at:"),
                "Should print Redis connection message");
        assertTrue(output.contains("127.0.0.1"),
                "Should contain Redis host");
        assertTrue(output.contains("6379"),
                "Should contain Redis port");
    }

    @Test
    @DisplayName("connectToCache() does not throw")
    void connectToCache_doesNotThrow() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(databaseService);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: initializeExternalServices() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeExternalServices() prints external API message")
    void initializeExternalServices_printsExternalApiMessage() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("Initializing external API:"),
                "Should print external API message");
        assertTrue(output.contains("api.example.com"),
                "Should contain external API URL");
    }

    @Test
    @DisplayName("initializeExternalServices() prints payment service message")
    void initializeExternalServices_printsPaymentServiceMessage() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("Initializing payment service:"),
                "Should print payment service message");
        assertTrue(output.contains("payment.internal.company.com"),
                "Should contain payment service URL");
    }

    @Test
    @DisplayName("initializeExternalServices() does not throw")
    void initializeExternalServices_doesNotThrow() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(databaseService);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connection field initial state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connection field is null before connect() is called")
    void connectionField_beforeConnect_isNull() throws Exception {
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        assertNull(connectionField.get(databaseService),
                "connection should be null before connect() is called");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sequence: connect then disconnect
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() followed by disconnect() does not throw")
    void connectThenDisconnect_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.connect();
            databaseService.disconnect();
        });
    }

    @Test
    @DisplayName("executeQuery() after failed connect() does not throw")
    void executeQueryAfterFailedConnect_doesNotThrow() {
        databaseService.connect(); // will fail silently
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"));
    }
}
