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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Tests cover all public/private methods, constructors, constants, and code paths.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates DatabaseService instance successfully")
    void constructor_default_createsInstance() {
        // Arrange & Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("DatabaseService instance is of correct type")
    void constructor_default_instanceOfDatabaseService() {
        // Arrange & Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertInstanceOf(DatabaseService.class, service);
    }

    @Test
    @DisplayName("New DatabaseService has null connection field")
    void constructor_default_connectionIsNull() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);

        // Act
        Object connection = connectionField.get(service);

        // Assert
        assertNull(connection, "connection field should be null before connect() is called");
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
    @DisplayName("DB_URL constant contains jdbc:mysql prefix")
    void staticField_dbUrl_containsJdbcMysql() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.startsWith("jdbc:mysql://"), "DB_URL should start with jdbc:mysql://");
    }

    @Test
    @DisplayName("DB_URL constant contains host, port and database name")
    void staticField_dbUrl_containsHostPortAndDbName() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
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
    @DisplayName("EXTERNAL_API_URL constant has correct value")
    void staticField_externalApiUrl_hasCorrectValue() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        assertEquals("http://api.example.com:8080/v1", field.get(null));
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant has correct value")
    void staticField_paymentServiceUrl_hasCorrectValue() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        assertEquals("https://payment.internal.company.com/process", field.get(null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() does not throw an exception when DB is unavailable")
    void connect_dbUnavailable_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(service::connect,
                "connect() should handle SQLException gracefully");
    }

    @Test
    @DisplayName("connect() prints connecting message")
    void connect_prints_connectingMessage() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.connect();

        // Assert
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Connecting to database"),
                "connect() should print 'Connecting to database'");
    }

    @Test
    @DisplayName("connect() prints error message when driver or DB is unavailable")
    void connect_dbUnavailable_printsErrorMessage() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.connect();

        // Assert – either connected (unlikely in CI) or printed an error
        String combined = outContent.toString() + errContent.toString();
        assertTrue(
                combined.contains("Connected to database") ||
                combined.contains("Database driver not found") ||
                combined.contains("Database connection failed"),
                "connect() should print a relevant message about the connection attempt"
        );
    }

    @Test
    @DisplayName("connect() leaves connection null when DB is unavailable")
    void connect_dbUnavailable_connectionRemainsNull() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.connect();

        // Assert
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        // Connection will be null because no real DB is available in test environment
        Object conn = connectionField.get(service);
        // We just verify no exception was thrown; connection may be null
        // (assertNull is valid here since no real DB is present)
        assertNull(conn, "connection should remain null when DB is unavailable");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connectToCache() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connectToCache() does not throw an exception")
    void connectToCache_doesNotThrow() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(service),
                "connectToCache() should not throw");
    }

    @Test
    @DisplayName("connectToCache() prints Redis connection message")
    void connectToCache_prints_redisMessage() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Connecting to Redis cache"),
                "connectToCache() should print Redis connection message");
    }

    @Test
    @DisplayName("connectToCache() message contains REDIS_HOST and REDIS_PORT")
    void connectToCache_message_containsHostAndPort() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("127.0.0.1"), "Message should contain Redis host");
        assertTrue(output.contains("6379"), "Message should contain Redis port");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeExternalServices() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeExternalServices() does not throw an exception")
    void initializeExternalServices_doesNotThrow() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(service));
    }

    @Test
    @DisplayName("initializeExternalServices() prints external API message")
    void initializeExternalServices_prints_externalApiMessage() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Initializing external API"),
                "Should print external API initialization message");
    }

    @Test
    @DisplayName("initializeExternalServices() prints payment service message")
    void initializeExternalServices_prints_paymentServiceMessage() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Initializing payment service"),
                "Should print payment service initialization message");
    }

    @Test
    @DisplayName("initializeExternalServices() message contains EXTERNAL_API_URL")
    void initializeExternalServices_message_containsApiUrl() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("http://api.example.com:8080/v1"),
                "Message should contain the external API URL");
    }

    @Test
    @DisplayName("initializeExternalServices() message contains PAYMENT_SERVICE_URL")
    void initializeExternalServices_message_containsPaymentUrl() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        // Act
        method.invoke(service);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("https://payment.internal.company.com/process"),
                "Message should contain the payment service URL");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() with null connection does not throw")
    void executeQuery_nullConnection_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();
        // connection is null by default

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("SELECT 1"),
                "executeQuery() should handle null connection gracefully");
    }

    @Test
    @DisplayName("executeQuery() with null connection produces no output")
    void executeQuery_nullConnection_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.executeQuery("SELECT 1");

        // Assert
        String combined = outContent.toString() + errContent.toString();
        // When connection is null, the if-block is skipped, so no output expected
        assertFalse(combined.contains("Executing query"),
                "executeQuery() should not print when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw")
    void executeQuery_emptySql_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(""),
                "executeQuery() should handle empty SQL gracefully");
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw")
    void executeQuery_nullSql_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(null),
                "executeQuery() should handle null SQL gracefully");
    }

    @Test
    @DisplayName("executeQuery() with complex SQL does not throw when connection is null")
    void executeQuery_complexSql_nullConnection_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();
        String complexSql = "SELECT u.id, u.name FROM users u WHERE u.active = 1 ORDER BY u.name";

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(complexSql));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() with null connection does not throw")
    void disconnect_nullConnection_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();
        // connection is null by default

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() should handle null connection gracefully");
    }

    @Test
    @DisplayName("disconnect() with null connection produces no output")
    void disconnect_nullConnection_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.disconnect();

        // Assert
        String combined = outContent.toString() + errContent.toString();
        assertFalse(combined.contains("Database connection closed"),
                "disconnect() should not print when connection is null");
    }

    @Test
    @DisplayName("disconnect() can be called multiple times without throwing")
    void disconnect_calledMultipleTimes_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> {
            service.disconnect();
            service.disconnect();
            service.disconnect();
        }, "disconnect() should be idempotent");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class-level structural tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DatabaseService has public connect() method")
    void classStructure_hasConnectMethod() throws Exception {
        Method method = DatabaseService.class.getMethod("connect");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "connect() should be public");
    }

    @Test
    @DisplayName("DatabaseService has public executeQuery(String) method")
    void classStructure_hasExecuteQueryMethod() throws Exception {
        Method method = DatabaseService.class.getMethod("executeQuery", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "executeQuery() should be public");
    }

    @Test
    @DisplayName("DatabaseService has public disconnect() method")
    void classStructure_hasDisconnectMethod() throws Exception {
        Method method = DatabaseService.class.getMethod("disconnect");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "disconnect() should be public");
    }

    @Test
    @DisplayName("DatabaseService has private connectToCache() method")
    void classStructure_hasConnectToCacheMethod() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "connectToCache() should be private");
    }

    @Test
    @DisplayName("DatabaseService has private initializeExternalServices() method")
    void classStructure_hasInitializeExternalServicesMethod() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "initializeExternalServices() should be private");
    }

    @Test
    @DisplayName("DatabaseService has private instance field 'connection'")
    void classStructure_hasConnectionField() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("connection");
        assertNotNull(field);
        assertTrue(Modifier.isPrivate(field.getModifiers()),
                "connection field should be private");
    }

    @Test
    @DisplayName("connect() and disconnect() lifecycle does not throw")
    void lifecycle_connectThenDisconnect_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> {
            service.connect();
            service.disconnect();
        }, "Full connect/disconnect lifecycle should not throw");
    }

    @Test
    @DisplayName("Full lifecycle: connect, executeQuery, disconnect does not throw")
    void lifecycle_fullCycle_doesNotThrow() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> {
            service.connect();
            service.executeQuery("SELECT 1");
            service.disconnect();
        }, "Full lifecycle should not throw");
    }
}
