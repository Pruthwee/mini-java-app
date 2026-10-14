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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp.
 *
 * Strategy:
 *  - main()                  → tested via output capture (file-system paths won't exist in CI)
 *  - initializeApplication() → invoked via reflection; verifies output
 *  - loadConfiguration()     → invoked via reflection; verifies missing-file warning path
 *  - initializeLogging()     → invoked via reflection; verifies output / no exception
 *  - startServer()           → invoked via reflection; verifies server-start output
 *  - constant fields         → verified via reflection
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private MiniApp miniApp;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsInstance() {
        MiniApp app = new MiniApp();
        assertNotNull(app, "MiniApp instance should not be null");
    }

    // -----------------------------------------------------------------------
    // main()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application...'")
    void main_printsStartingMessage() {
        // Act
        MiniApp.main(new String[]{});

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application..."),
                "main() should print starting message");
    }

    @Test
    @DisplayName("main() does not throw any exception")
    void main_doesNotThrowException() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not propagate any exception");
    }

    @Test
    @DisplayName("main() with null args does not throw")
    void main_withNullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should handle null args gracefully");
    }

    @Test
    @DisplayName("main() with non-empty args array does not throw")
    void main_withNonEmptyArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"--debug", "--port=9090"}),
                "main() should handle extra args without throwing");
    }

    // -----------------------------------------------------------------------
    // initializeApplication() – private, invoked via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("initializeApplication() does not throw")
    void initializeApplication_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeApplication() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeApplication() triggers loadConfiguration output")
    void initializeApplication_triggersLoadConfiguration() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        method.invoke(miniApp);

        String combined = outContent.toString() + errContent.toString();
        // Config file won't exist in CI → warning message expected
        assertTrue(combined.contains("Configuration") || combined.contains("config"),
                "initializeApplication() should trigger configuration loading output");
    }

    // -----------------------------------------------------------------------
    // loadConfiguration() – private, invoked via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadConfiguration() prints warning when config file is missing")
    void loadConfiguration_whenFileNotFound_printsWarning() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        method.invoke(miniApp);

        String output = outContent.toString();
        // /opt/app/config/app.properties won't exist in CI
        assertTrue(output.contains("Warning: Configuration file not found at:"),
                "Should print warning when config file is absent");
    }

    @Test
    @DisplayName("loadConfiguration() does not throw when config file is missing")
    void loadConfiguration_whenFileNotFound_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() should handle missing file without throwing");
    }

    // -----------------------------------------------------------------------
    // initializeLogging() – private, invoked via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("initializeLogging() does not throw")
    void initializeLogging_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeLogging() produces some output or error output")
    void initializeLogging_producesOutput() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        method.invoke(miniApp);

        String combined = outContent.toString() + errContent.toString();
        // Either "Logging initialized" (success) or "Failed to initialize logging" (permission denied)
        boolean hasExpectedOutput =
                combined.contains("Logging initialized") ||
                combined.contains("Failed to initialize logging");
        assertTrue(hasExpectedOutput,
                "initializeLogging() should produce logging-related output");
    }

    // -----------------------------------------------------------------------
    // startServer() – private, invoked via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("startServer() does not throw")
    void startServer_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() should not propagate exceptions");
    }

    @Test
    @DisplayName("startServer() prints server started message or error message")
    void startServer_printsServerStartedOrError() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        method.invoke(miniApp);

        String combined = outContent.toString() + errContent.toString();
        boolean hasExpectedOutput =
                combined.contains("Server started on port:") ||
                combined.contains("Failed to start server:");
        assertTrue(hasExpectedOutput,
                "startServer() should print server started or error message");
    }

    // -----------------------------------------------------------------------
    // Static constant field verification via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void constants_serverPort_is8080() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(8080, field.get(null),
                "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void constants_configFilePath_isExpected() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/opt/app/config/app.properties", field.get(null),
                "CONFIG_FILE_PATH should match expected value");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void constants_logFilePath_isExpected() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/var/log/mini-app.log", field.get(null),
                "LOG_FILE_PATH should match expected value");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant ends with '.properties'")
    void constants_configFilePath_endsWithProperties() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".properties"),
                "CONFIG_FILE_PATH should end with '.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant ends with '.log'")
    void constants_logFilePath_endsWithLog() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".log"),
                "LOG_FILE_PATH should end with '.log'");
    }

    @Test
    @DisplayName("SERVER_PORT constant is a positive integer")
    void constants_serverPort_isPositive() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port > 0, "SERVER_PORT should be a positive integer");
    }

    @Test
    @DisplayName("SERVER_PORT constant is within valid port range (1-65535)")
    void constants_serverPort_isInValidRange() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port >= 1 && port <= 65535,
                "SERVER_PORT should be within valid port range 1-65535");
    }
}
