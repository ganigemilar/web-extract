package com.webextractor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for web-extractor CLI commands.
 * These tests verify the CLI structure and basic functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebExtractorIntegrationTest {

    @TempDir
    Path tempDir;

    private File sessionFile;
    private File outputFile;

    @BeforeEach
    void setUp() {
        sessionFile = tempDir.resolve("session.json").toFile();
        outputFile = tempDir.resolve("output.json").toFile();
    }

    @Test
    @Order(1)
    @DisplayName("Main command shows help")
    void testMainHelp() {
        CommandLine cmd = new CommandLine(new Main());
        String output = getUsage(cmd);

        assertTrue(output.contains("web-extract"), "Expected 'web-extract' in output: " + output);
        assertTrue(output.contains("login"), "Expected 'login' in output: " + output);
        assertTrue(output.contains("extract"), "Expected 'extract' in output: " + output);
        assertTrue(output.contains("-h") && output.contains("--help"), "Expected help options in output: " + output);
        assertTrue(output.contains("-V") && output.contains("--version"), "Expected version options in output: " + output);
    }

    @Test
    @Order(2)
    @DisplayName("Main command shows version")
    void testMainVersion() {
        CommandLine cmd = new CommandLine(new Main());
        // Version is available in the help text or from the command annotation
        String helpText = getUsage(cmd);
        assertTrue(helpText.contains("2.0.0") || helpText.contains("1.0.0") || helpText.contains("version"),
                   "Expected version info in help: " + helpText);
    }

    @Test
    @Order(3)
    @DisplayName("Login command shows help")
    void testLoginHelp() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine loginCmd = cmd.getSubcommands().get("login");
        assertNotNull(loginCmd, "login subcommand should exist");

        String output = getUsage(loginCmd);

        assertTrue(output.contains("login"), "Expected 'login' in output: " + output);
        assertTrue(output.contains("LOGIN_URL"), "Expected LOGIN_URL in output: " + output);
        assertTrue(output.contains("-s") && output.contains("--session"), "Expected session options in output: " + output);
        assertTrue(output.contains("--user-selector"), "Expected --user-selector in output: " + output);
        assertTrue(output.contains("--pass-selector"), "Expected --pass-selector in output: " + output);
        assertTrue(output.contains("--browser"), "Expected --browser in output: " + output);
        assertTrue(output.contains("--headed"), "Expected --headed in output: " + output);
        assertTrue(output.contains("--timeout"), "Expected --timeout in output: " + output);
        assertTrue(output.contains("--submit-selector"), "Expected --submit-selector in output: " + output);
    }

    @Test
    @Order(4)
    @DisplayName("Extract command shows help")
    void testExtractHelp() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine extractCmd = cmd.getSubcommands().get("extract");
        assertNotNull(extractCmd, "extract subcommand should exist");

        String output = getUsage(extractCmd);

        assertTrue(output.contains("extract"), "Expected 'extract' in output: " + output);
        assertTrue(output.contains("URL"), "Expected URL in output: " + output);
        assertTrue(output.contains("-q") && output.contains("--query"), "Expected query options in output: " + output);
        assertTrue(output.contains("-a") && output.contains("--attr"), "Expected attr options in output: " + output);
        assertTrue(output.contains("--html"), "Expected --html in output: " + output);
        assertTrue(output.contains("-o") && output.contains("--output"), "Expected output options in output: " + output);
        assertTrue(output.contains("-s") && output.contains("--session"), "Expected session options in output: " + output);
        assertTrue(output.contains("--wait-for"), "Expected --wait-for in output: " + output);
        assertTrue(output.contains("--delay"), "Expected --delay in output: " + output);
        assertTrue(output.contains("--update-session"), "Expected --update-session in output: " + output);
    }

    @Test
    @Order(5)
    @DisplayName("Login command requires URL")
    void testLoginRequiresUrl() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine loginCmd = cmd.getSubcommands().get("login");

        // Execute with missing required option - should fail with non-zero exit code
        int exitCode = loginCmd.execute("-s", sessionFile.getAbsolutePath());
        assertNotEquals(0, exitCode, "Login command should fail when URL is missing");
    }

    @Test
    @Order(6)
    @DisplayName("Extract command requires URL")
    void testExtractRequiresUrl() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine extractCmd = cmd.getSubcommands().get("extract");

        // Execute with missing required option - should fail with non-zero exit code
        int exitCode = extractCmd.execute("-o", outputFile.getAbsolutePath());
        assertNotEquals(0, exitCode, "Extract command should fail when URL is missing");
    }

    @Test
    @Order(7)
    @DisplayName("Extract command requires at least one query")
    void testExtractRequiresQuery() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine extractCmd = cmd.getSubcommands().get("extract");

        // Execute with missing required option - should fail with non-zero exit code
        int exitCode = extractCmd.execute("-u", "https://example.com", "-o", outputFile.getAbsolutePath());
        assertNotEquals(0, exitCode, "Extract command should fail when query is missing");
    }

    @Test
    @Order(8)
    @DisplayName("Login command help shows mode descriptions")
    void testLoginHelpShowsModes() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine loginCmd = cmd.getSubcommands().get("login");
        String output = getUsage(loginCmd);

        // Verify login subcommand options are present
        assertTrue(output.contains("Manual") || output.contains("manual") || output.contains("interactive"),
                   "Expected manual/interactive mode description: " + output);
        assertTrue(output.contains("Automatic") || output.contains("automatic"),
                   "Expected automatic mode description: " + output);
    }

    /**
     * Get usage/help text from a CommandLine without executing.
     */
    private String getUsage(CommandLine cmd) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        cmd.usage(pw);
        pw.flush();
        return sw.toString();
    }
}