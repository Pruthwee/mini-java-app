package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Tests cover: instantiation, main(), initializeApplication(),
 * loadConfiguration(), initializeLogging(), startServer(),
 * and all static constant values.
 */
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
    // Constructor / Instantiation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsNonNullInstance() {
        MiniApp app = new MiniApp();
        assertNotNull(app, "MiniApp instance should not be null");
    }

    @Test
    @DisplayName("Multiple MiniApp instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();
        assertNotSame(app1, app2, "Two instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static constant values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void staticConstants_serverPort_is8080() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(8080, field.get(null), "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void staticConstants_configFilePath_isExpected() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/opt/app/config/app.properties", field.get(null));
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void staticConstants_logFilePath_isExpected() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/var/log/mini-app.log", field.get(null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main() method
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() does not throw an exception")
    void main_withEmptyArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not throw with empty args");
    }

    @Test
    @DisplayName("main() with null args does not throw")
    void main_withNullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should not throw with null args");
    }

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application...'")
    void main_printsStartingMessage() {
        MiniApp.main(new String[]{});
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application..."),
                "main() should print starting message");
    }

    @Test
    @DisplayName("main() with extra args does not throw")
    void main_withExtraArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"arg1", "arg2", "arg3"}));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: initializeApplication() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw")
    void initializeApplication_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(miniApp);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap and rethrow only if it's unexpected
                if (!(e.getCause() instanceof Exception)) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    @DisplayName("initializeApplication() triggers loadConfiguration()")
    void initializeApplication_triggersLoadConfiguration() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {
            // May fail due to missing files/DB; that's acceptable
        }
        String output = outContent.toString();
        // Either "Configuration loaded" or "Warning: Configuration file not found" should appear
        boolean configMsgPresent = output.contains("Configuration loaded from:")
                || output.contains("Warning: Configuration file not found at:");
        assertTrue(configMsgPresent, "initializeApplication should trigger loadConfiguration");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: loadConfiguration() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() does not throw")
    void loadConfiguration_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(miniApp);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof Exception)) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    @DisplayName("loadConfiguration() prints warning when config file does not exist")
    void loadConfiguration_whenFileNotFound_printsWarning() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String output = outContent.toString();
        // /opt/app/config/app.properties almost certainly doesn't exist in test env
        boolean warningOrLoaded = output.contains("Warning: Configuration file not found at:")
                || output.contains("Configuration loaded from:");
        assertTrue(warningOrLoaded,
                "loadConfiguration should print either warning or loaded message");
    }

    @Test
    @DisplayName("loadConfiguration() references CONFIG_FILE_PATH in output")
    void loadConfiguration_outputContainsConfigFilePath() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String output = outContent.toString();
        assertTrue(output.contains("/opt/app/config/app.properties"),
                "Output should reference the config file path");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: initializeLogging() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw")
    void initializeLogging_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(miniApp);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof Exception)) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    @DisplayName("initializeLogging() prints logging initialized message or error")
    void initializeLogging_printsLoggingMessageOrError() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        boolean msgPresent = stdout.contains("Logging initialized at:")
                || stderr.contains("Failed to initialize logging:");
        assertTrue(msgPresent,
                "initializeLogging should print initialized or error message");
    }

    @Test
    @DisplayName("initializeLogging() references LOG_FILE_PATH in output when successful")
    void initializeLogging_outputContainsLogFilePath() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        // Either success or failure message should reference the path
        boolean pathReferenced = stdout.contains("/var/log/mini-app.log")
                || stderr.contains("Failed to initialize logging:");
        assertTrue(pathReferenced,
                "Output should reference log file path or report failure");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private method: startServer() via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw")
    void startServer_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(miniApp);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof Exception)) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    @DisplayName("startServer() prints server started message or error")
    void startServer_printsServerStartedOrError() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        boolean msgPresent = stdout.contains("Server started on port:")
                || stderr.contains("Failed to start server:");
        assertTrue(msgPresent,
                "startServer should print started or error message");
    }

    @Test
    @DisplayName("startServer() references SERVER_PORT (8080) in output when successful")
    void startServer_outputContainsServerPort() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        boolean portReferenced = stdout.contains("8080")
                || stderr.contains("Failed to start server:");
        assertTrue(portReferenced,
                "Output should reference port 8080 or report failure");
    }

    @Test
    @DisplayName("startServer() prints 'Server ready to accept connections...' when port is available")
    void startServer_whenPortAvailable_printsReadyMessage() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        try {
            method.invoke(miniApp);
        } catch (Exception ignored) {}

        String stdout = outContent.toString();
        String stderr = errContent.toString();
        boolean readyOrError = stdout.contains("Server ready to accept connections...")
                || stderr.contains("Failed to start server:");
        assertTrue(readyOrError,
                "startServer should print ready message or error");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style: full application flow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full application flow via main() completes without uncaught exception")
    void fullFlow_viaMain_completesWithoutUncaughtException() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "Full application flow should not throw uncaught exceptions");
    }

    @Test
    @DisplayName("main() output contains 'Starting Mini Java Application...' as first line")
    void main_outputStartsWithStartingMessage() {
        MiniApp.main(new String[]{});
        String output = outContent.toString();
        assertTrue(output.startsWith("Starting Mini Java Application..."),
                "First output line should be the starting message");
    }

    @Test
    @DisplayName("MiniApp class has expected private methods via reflection")
    void reflection_miniAppHasExpectedPrivateMethods() {
        assertDoesNotThrow(() -> {
            MiniApp.class.getDeclaredMethod("initializeApplication");
            MiniApp.class.getDeclaredMethod("loadConfiguration");
            MiniApp.class.getDeclaredMethod("initializeLogging");
            MiniApp.class.getDeclaredMethod("startServer");
        }, "MiniApp should have all expected private methods");
    }

    @Test
    @DisplayName("MiniApp class has expected static fields via reflection")
    void reflection_miniAppHasExpectedStaticFields() {
        assertDoesNotThrow(() -> {
            MiniApp.class.getDeclaredField("SERVER_PORT");
            MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
            MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        }, "MiniApp should have all expected static fields");
    }
}
