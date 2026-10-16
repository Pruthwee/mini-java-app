package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Covers main(), initializeApplication(), loadConfiguration(),
 * initializeLogging(), startServer(), and all static constants.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private MiniApp miniApp;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        miniApp = new MiniApp();
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
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsInstance() {
        MiniApp app = new MiniApp();
        assertNotNull(app, "MiniApp instance should not be null");
    }

    @Test
    @DisplayName("Multiple MiniApp instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();
        assertNotSame(app1, app2, "Each MiniApp() call should produce a distinct instance");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void staticField_serverPort_is8080() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(8080, field.get(null), "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void staticField_configFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/opt/app/config/app.properties", field.get(null));
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void staticField_logFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/var/log/mini-app.log", field.get(null));
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant starts with '/'")
    void staticField_configFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"), "CONFIG_FILE_PATH should be an absolute path");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant starts with '/'")
    void staticField_logFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"), "LOG_FILE_PATH should be an absolute path");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() Tests (private – via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() does not throw when config file is absent")
    void loadConfiguration_configFileAbsent_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() should not throw when config file is missing");
    }

    @Test
    @DisplayName("loadConfiguration() prints warning when config file is absent")
    void loadConfiguration_configFileAbsent_printsWarning() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        method.invoke(miniApp);

        String output = outContent.toString();
        // The hardcoded path /opt/app/config/app.properties won't exist in CI
        assertTrue(
            output.contains("Warning: Configuration file not found at:") ||
            output.contains("Configuration loaded from:"),
            "Should print either a warning or a loaded message"
        );
    }

    @Test
    @DisplayName("loadConfiguration() prints 'Warning: Configuration file not found' for non-existent path")
    void loadConfiguration_nonExistentPath_printsWarning() throws Exception {
        // The hardcoded path /opt/app/config/app.properties should not exist in test env
        File configFile = new File("/opt/app/config/app.properties");
        if (!configFile.exists()) {
            Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
            method.setAccessible(true);
            method.invoke(miniApp);

            assertTrue(outContent.toString().contains("Warning: Configuration file not found at:"),
                    "Should warn about missing config file");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() Tests (private – via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw regardless of /var/log permissions")
    void initializeLogging_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeLogging() prints 'Logging initialized at:' or error message")
    void initializeLogging_printsLoggingMessage() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        method.invoke(miniApp);

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        assertTrue(
            stdout.contains("Logging initialized at:") ||
            stderr.contains("Failed to initialize logging:"),
            "Should print either success or failure message for logging initialization"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() Tests (private – via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw (binds or fails gracefully)")
    void startServer_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() should not propagate exceptions");
    }

    @Test
    @DisplayName("startServer() prints 'Server started on port:' or error message")
    void startServer_printsServerStartedOrError() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        method.invoke(miniApp);

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        assertTrue(
            stdout.contains("Server started on port:") ||
            stderr.contains("Failed to start server:"),
            "Should print either server started or failure message"
        );
    }

    @Test
    @DisplayName("startServer() prints port 8080 in success message when port is available")
    void startServer_whenPortAvailable_printsPort8080() throws Exception {
        // Only assert port message if port 8080 is actually available
        boolean portAvailable = false;
        try (ServerSocket ss = new ServerSocket(8080)) {
            portAvailable = true;
        } catch (IOException ignored) {
            // port in use – skip assertion
        }

        if (portAvailable) {
            Method method = MiniApp.class.getDeclaredMethod("startServer");
            method.setAccessible(true);
            method.invoke(miniApp);

            assertTrue(outContent.toString().contains("8080"),
                    "startServer() should print port 8080 when port is available");
        }
    }

    @Test
    @DisplayName("startServer() prints 'Server ready to accept connections...' on success")
    void startServer_whenPortAvailable_printsReadyMessage() throws Exception {
        boolean portAvailable = false;
        try (ServerSocket ss = new ServerSocket(8080)) {
            portAvailable = true;
        } catch (IOException ignored) {
            // port in use – skip assertion
        }

        if (portAvailable) {
            Method method = MiniApp.class.getDeclaredMethod("startServer");
            method.setAccessible(true);
            method.invoke(miniApp);

            assertTrue(outContent.toString().contains("Server ready to accept connections..."),
                    "startServer() should print ready message");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() Tests (private – via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw")
    void initializeApplication_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeApplication() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeApplication() triggers loadConfiguration() output")
    void initializeApplication_triggersLoadConfiguration() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        method.invoke(miniApp);

        String combined = outContent.toString() + errContent.toString();
        // loadConfiguration() always prints something (warning or loaded)
        assertTrue(
            combined.contains("Configuration") || combined.contains("configuration"),
            "initializeApplication() should trigger configuration loading"
        );
    }

    @Test
    @DisplayName("initializeApplication() triggers database connect() output")
    void initializeApplication_triggersDatabaseConnect() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        method.invoke(miniApp);

        String combined = outContent.toString() + errContent.toString();
        assertTrue(
            combined.contains("Connecting to database") ||
            combined.contains("Database connection failed"),
            "initializeApplication() should trigger database connection attempt"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() does not throw with empty args array")
    void main_withEmptyArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not throw with empty args");
    }

    @Test
    @DisplayName("main() does not throw with null args")
    void main_withNullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should not throw with null args");
    }

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application...'")
    void main_printsStartingMessage() {
        MiniApp.main(new String[]{});
        assertTrue(outContent.toString().contains("Starting Mini Java Application..."),
                "main() should print starting message");
    }

    @Test
    @DisplayName("main() with extra args does not throw")
    void main_withExtraArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"arg1", "arg2", "arg3"}),
                "main() should not throw with extra arguments");
    }

    @Test
    @DisplayName("main() completes without hanging indefinitely")
    void main_completesWithinReasonableTime() {
        long start = System.currentTimeMillis();
        MiniApp.main(new String[]{});
        long elapsed = System.currentTimeMillis() - start;
        // Should complete within 10 seconds (server sleeps 1 second)
        assertTrue(elapsed < 10_000,
                "main() should complete within 10 seconds, took: " + elapsed + "ms");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class Structure Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class has a public static main method")
    void classStructure_hasPublicStaticMainMethod() throws Exception {
        java.lang.reflect.Method mainMethod = MiniApp.class.getMethod("main", String[].class);
        assertNotNull(mainMethod, "MiniApp should have a public static main method");
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()),
                "main() should be static");
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()),
                "main() should be public");
    }

    @Test
    @DisplayName("MiniApp class has private initializeApplication method")
    void classStructure_hasPrivateInitializeApplicationMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeApplication() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private loadConfiguration method")
    void classStructure_hasPrivateLoadConfigurationMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "loadConfiguration() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private initializeLogging method")
    void classStructure_hasPrivateInitializeLoggingMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeLogging() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private startServer method")
    void classStructure_hasPrivateStartServerMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "startServer() should be private");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge Case / Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT is a positive integer")
    void serverPort_isPositiveInteger() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port > 0, "SERVER_PORT should be a positive integer");
        assertTrue(port <= 65535, "SERVER_PORT should be within valid port range");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH is not null or empty")
    void configFilePath_isNotNullOrEmpty() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertNotNull(path, "CONFIG_FILE_PATH should not be null");
        assertFalse(path.isEmpty(), "CONFIG_FILE_PATH should not be empty");
    }

    @Test
    @DisplayName("LOG_FILE_PATH is not null or empty")
    void logFilePath_isNotNullOrEmpty() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertNotNull(path, "LOG_FILE_PATH should not be null");
        assertFalse(path.isEmpty(), "LOG_FILE_PATH should not be empty");
    }

    @Test
    @DisplayName("LOG_FILE_PATH ends with '.log'")
    void logFilePath_endsWithLogExtension() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".log"), "LOG_FILE_PATH should end with .log extension");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH ends with '.properties'")
    void configFilePath_endsWithPropertiesExtension() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".properties"),
                "CONFIG_FILE_PATH should end with .properties extension");
    }
}
