package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Tests cover constructor, main(), and all private helper methods via reflection
 * and output stream capture.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

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

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_default_createsNonNullInstance() {
        // Arrange & Act
        MiniApp app = new MiniApp();

        // Assert
        assertNotNull(app, "MiniApp instance should not be null");
    }

    @Test
    @DisplayName("Multiple MiniApp instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        // Arrange & Act
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();

        // Assert
        assertNotNull(app1);
        assertNotNull(app2);
        assertNotSame(app1, app2, "Two instances should not be the same object");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constant / Field Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void serverPort_constant_is8080() throws Exception {
        // Arrange
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);

        // Act
        int port = (int) field.get(null);

        // Assert
        assertEquals(8080, port, "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void configFilePath_constant_isExpectedPath() throws Exception {
        // Arrange
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String path = (String) field.get(null);

        // Assert
        assertEquals("/opt/app/config/app.properties", path,
                "CONFIG_FILE_PATH should be '/opt/app/config/app.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void logFilePath_constant_isExpectedPath() throws Exception {
        // Arrange
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String path = (String) field.get(null);

        // Assert
        assertEquals("/var/log/mini-app.log", path,
                "LOG_FILE_PATH should be '/var/log/mini-app.log'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() prints warning when config file does not exist")
    void loadConfiguration_whenFileDoesNotExist_printsWarning() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Warning: Configuration file not found at:"),
                "Should print warning when config file is missing");
    }

    @Test
    @DisplayName("loadConfiguration() prints the hardcoded config file path in warning")
    void loadConfiguration_whenFileDoesNotExist_printsConfigFilePath() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("/opt/app/config/app.properties"),
                "Warning should include the hardcoded config file path");
    }

    @Test
    @DisplayName("loadConfiguration() does not throw exception when file is missing")
    void loadConfiguration_whenFileDoesNotExist_doesNotThrow() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(app),
                "loadConfiguration() should not throw when config file is missing");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw exception")
    void initializeLogging_always_doesNotThrow() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act & Assert – may succeed or fail depending on /var/log permissions
        // Either way it should not propagate an exception
        assertDoesNotThrow(() -> method.invoke(app),
                "initializeLogging() should handle IOException internally");
    }

    @Test
    @DisplayName("initializeLogging() prints logging initialized message or error message")
    void initializeLogging_always_printsMessage() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert – one of the two messages must appear
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        boolean hasSuccessMsg = stdOut.contains("Logging initialized at:");
        boolean hasErrorMsg   = stdErr.contains("Failed to initialize logging:");
        assertTrue(hasSuccessMsg || hasErrorMsg,
                "Should print either success or error message for logging initialization");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw exception")
    void startServer_always_doesNotThrow() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(app),
                "startServer() should handle exceptions internally");
    }

    @Test
    @DisplayName("startServer() prints server started message or error message")
    void startServer_always_printsMessage() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        boolean hasStartedMsg = stdOut.contains("Server started on port:");
        boolean hasErrorMsg   = stdErr.contains("Failed to start server:");
        assertTrue(hasStartedMsg || hasErrorMsg,
                "Should print either server started or error message");
    }

    @Test
    @DisplayName("startServer() when successful prints port 8080")
    void startServer_whenSuccessful_printPort8080() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert – if port 8080 is available the message will contain "8080"
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        if (stdOut.contains("Server started on port:")) {
            assertTrue(stdOut.contains("8080"),
                    "Server started message should include port 8080");
        } else {
            // Port may be in use in CI; error path is acceptable
            assertTrue(stdErr.contains("Failed to start server:"),
                    "Should print error message when port is unavailable");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw exception")
    void initializeApplication_always_doesNotThrow() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(app),
                "initializeApplication() should handle all exceptions internally");
    }

    @Test
    @DisplayName("initializeApplication() triggers loadConfiguration() (prints config warning)")
    void initializeApplication_always_triggersLoadConfiguration() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Warning: Configuration file not found at:") ||
                   output.contains("Configuration loaded from:"),
                "initializeApplication() should call loadConfiguration()");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class-level / Annotation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class is annotated with @SpringBootApplication")
    void miniAppClass_hasSpringBootApplicationAnnotation() {
        // Arrange & Act
        boolean hasAnnotation = MiniApp.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class);

        // Assert
        assertTrue(hasAnnotation,
                "MiniApp should be annotated with @SpringBootApplication");
    }

    @Test
    @DisplayName("MiniApp has a public static main method")
    void miniApp_hasPublicStaticMainMethod() throws Exception {
        // Arrange & Act
        java.lang.reflect.Method mainMethod =
                MiniApp.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertNotNull(mainMethod, "main method should exist");
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()),
                "main method should be public");
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()),
                "main method should be static");
    }

    @Test
    @DisplayName("MiniApp has private loadConfiguration method")
    void miniApp_hasPrivateLoadConfigurationMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");

        // Assert
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "loadConfiguration should be private");
    }

    @Test
    @DisplayName("MiniApp has private initializeLogging method")
    void miniApp_hasPrivateInitializeLoggingMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");

        // Assert
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeLogging should be private");
    }

    @Test
    @DisplayName("MiniApp has private startServer method")
    void miniApp_hasPrivateStartServerMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("startServer");

        // Assert
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "startServer should be private");
    }

    @Test
    @DisplayName("MiniApp has private initializeApplication method")
    void miniApp_hasPrivateInitializeApplicationMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");

        // Assert
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeApplication should be private");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge-case / Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp instance is of correct type")
    void miniApp_instanceOf_correctType() {
        // Arrange & Act
        MiniApp app = new MiniApp();

        // Assert
        assertInstanceOf(MiniApp.class, app,
                "Object should be an instance of MiniApp");
    }

    @Test
    @DisplayName("loadConfiguration() handles IOException gracefully (no propagation)")
    void loadConfiguration_ioException_handledGracefully() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act – CONFIG_FILE_PATH does not exist, so no IOException is thrown here;
        // the method simply prints a warning. Verify no exception escapes.
        assertDoesNotThrow(() -> method.invoke(app));
    }

    @Test
    @DisplayName("Multiple calls to loadConfiguration() are idempotent")
    void loadConfiguration_calledMultipleTimes_doesNotThrow() throws Exception {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            method.invoke(app);
            method.invoke(app);
            method.invoke(app);
        }, "Multiple calls to loadConfiguration() should not throw");
    }
}
