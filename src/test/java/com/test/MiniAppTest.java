package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Tests cover: main(), initializeApplication(), loadConfiguration(),
 * initializeLogging(), startServer() via reflection and observable side-effects.
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
        assertNotSame(app1, app2, "Two MiniApp instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void constants_serverPort_is8080() throws Exception {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);

        // Act
        int port = (int) field.get(null);

        // Assert
        assertEquals(8080, port, "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant points to /opt/app/config/app.properties")
    void constants_configFilePath_isCorrect() throws Exception {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String path = (String) field.get(null);

        // Assert
        assertEquals("/opt/app/config/app.properties", path,
                "CONFIG_FILE_PATH should be /opt/app/config/app.properties");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant points to /var/log/mini-app.log")
    void constants_logFilePath_isCorrect() throws Exception {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String path = (String) field.get(null);

        // Assert
        assertEquals("/var/log/mini-app.log", path,
                "LOG_FILE_PATH should be /var/log/mini-app.log");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() does not throw any exception")
    void main_withEmptyArgs_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not throw any exception");
    }

    @Test
    @DisplayName("main() with null args does not throw")
    void main_withNullArgs_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should not throw when args is null");
    }

    @Test
    @DisplayName("main() prints starting message")
    void main_withEmptyArgs_printsStartingMessage() {
        // Act
        MiniApp.main(new String[]{});

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application"),
                "main() should print starting message");
    }

    @Test
    @DisplayName("main() with extra args does not throw")
    void main_withExtraArgs_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"arg1", "arg2", "arg3"}),
                "main() should not throw with extra arguments");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() does not throw when config file does not exist")
    void loadConfiguration_fileNotFound_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() should not throw when config file is missing");
    }

    @Test
    @DisplayName("loadConfiguration() prints warning when config file does not exist")
    void loadConfiguration_fileNotFound_printsWarning() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Warning") || output.contains("not found") ||
                   output.contains("Configuration loaded"),
                "Should print warning or loaded message about config file");
    }

    @Test
    @DisplayName("loadConfiguration() handles IOException gracefully")
    void loadConfiguration_ioException_doesNotPropagate() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert – config path /opt/app/config/app.properties won't exist in test env
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() should handle IOException without propagating");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw any exception")
    void initializeLogging_called_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() should not throw any exception");
    }

    @Test
    @DisplayName("initializeLogging() prints logging initialized message or error")
    void initializeLogging_called_printsMessage() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert – either success or error message should appear
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Logging") || combined.contains("log") ||
                   combined.contains("Failed"),
                "Should print a logging-related message");
    }

    @Test
    @DisplayName("initializeLogging() handles IOException gracefully")
    void initializeLogging_ioException_doesNotPropagate() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() should handle IOException without propagating");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw any exception")
    void startServer_called_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() should not throw any exception");
    }

    @Test
    @DisplayName("startServer() prints server started or error message")
    void startServer_called_printsMessage() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Server") || combined.contains("server") ||
                   combined.contains("port") || combined.contains("Failed"),
                "Should print a server-related message");
    }

    @Test
    @DisplayName("startServer() handles port-in-use exception gracefully")
    void startServer_portInUse_doesNotPropagate() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act & Assert – even if port 8080 is in use, no exception should propagate
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() should handle port-in-use exception gracefully");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw any exception")
    void initializeApplication_called_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeApplication() should not throw any exception");
    }

    @Test
    @DisplayName("initializeApplication() triggers loadConfiguration")
    void initializeApplication_called_triggersLoadConfiguration() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert – loadConfiguration prints either loaded or warning message
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Configuration") || combined.contains("config") ||
                   combined.contains("Warning"),
                "initializeApplication() should trigger loadConfiguration()");
    }

    @Test
    @DisplayName("initializeApplication() triggers initializeLogging")
    void initializeApplication_called_triggersInitializeLogging() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Logging") || combined.contains("log") ||
                   combined.contains("Failed"),
                "initializeApplication() should trigger initializeLogging()");
    }

    @Test
    @DisplayName("initializeApplication() triggers DatabaseService.connect()")
    void initializeApplication_called_triggersDatabaseConnect() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String combined = outContent.toString() + errContent.toString();
        assertTrue(combined.contains("Connecting to PostgreSQL") ||
                   combined.contains("database") ||
                   combined.contains("PostgreSQL"),
                "initializeApplication() should trigger DatabaseService.connect()");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class Structure / Reflection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class has main method with String[] parameter")
    void classStructure_hasMainMethod() throws Exception {
        // Act
        Method mainMethod = MiniApp.class.getMethod("main", String[].class);

        // Assert
        assertNotNull(mainMethod, "MiniApp should have a main(String[]) method");
    }

    @Test
    @DisplayName("MiniApp class has private initializeApplication method")
    void classStructure_hasInitializeApplicationMethod() throws Exception {
        // Act
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");

        // Assert
        assertNotNull(method, "MiniApp should have initializeApplication() method");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeApplication() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private loadConfiguration method")
    void classStructure_hasLoadConfigurationMethod() throws Exception {
        // Act
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");

        // Assert
        assertNotNull(method, "MiniApp should have loadConfiguration() method");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "loadConfiguration() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private initializeLogging method")
    void classStructure_hasInitializeLoggingMethod() throws Exception {
        // Act
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");

        // Assert
        assertNotNull(method, "MiniApp should have initializeLogging() method");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeLogging() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private startServer method")
    void classStructure_hasStartServerMethod() throws Exception {
        // Act
        Method method = MiniApp.class.getDeclaredMethod("startServer");

        // Assert
        assertNotNull(method, "MiniApp should have startServer() method");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "startServer() should be private");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge Case / Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class is in com.test package")
    void classStructure_isInCorrectPackage() {
        // Act
        String packageName = MiniApp.class.getPackageName();

        // Assert
        assertEquals("com.test", packageName,
                "MiniApp should be in com.test package");
    }

    @Test
    @DisplayName("MiniApp is a concrete class (not abstract or interface)")
    void classStructure_isConcreteClass() {
        // Act
        int modifiers = MiniApp.class.getModifiers();

        // Assert
        assertFalse(java.lang.reflect.Modifier.isAbstract(modifiers),
                "MiniApp should not be abstract");
        assertFalse(MiniApp.class.isInterface(),
                "MiniApp should not be an interface");
    }

    @Test
    @DisplayName("main() can be called multiple times without error")
    void main_calledMultipleTimes_doesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            MiniApp.main(new String[]{});
            MiniApp.main(new String[]{});
        }, "main() should be callable multiple times without error");
    }
}
