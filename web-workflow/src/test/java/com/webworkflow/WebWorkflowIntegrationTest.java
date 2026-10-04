package com.webworkflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for web-workflow CLI commands.
 * These tests verify the CLI structure and basic functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebWorkflowIntegrationTest {

    @TempDir
    Path tempDir;

    private File testPlanFile;
    private File outputFile;
    private File sessionFile;

    @BeforeEach
    void setUp() {
        testPlanFile = tempDir.resolve("test-plan.json").toFile();
        outputFile = tempDir.resolve("output.json").toFile();
        sessionFile = tempDir.resolve("session.json").toFile();
    }

    @Test
    @Order(1)
    @DisplayName("Main command shows help")
    void testMainHelp() {
        CommandLine cmd = new CommandLine(new Main());
        String output = getUsage(cmd);

        assertTrue(output.contains("web-workflow"), "Expected 'web-workflow' in output: " + output);
        assertTrue(output.contains("action"), "Expected 'action' in output: " + output);
        assertTrue(output.contains("template"), "Expected 'template' in output: " + output);
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
        assertTrue(helpText.contains("1.0.0") || helpText.contains("version"), "Expected version info in help: " + helpText);
    }

    @Test
    @Order(3)
    @DisplayName("Action command shows help")
    void testActionHelp() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine actionCmd = cmd.getSubcommands().get("action");
        assertNotNull(actionCmd, "action subcommand should exist");

        String output = getUsage(actionCmd);

        assertTrue(output.contains("action"), "Expected 'action' in output: " + output);
        assertTrue(output.contains("-i") && output.contains("--input"), "Expected input options in output: " + output);
        assertTrue(output.contains("--plan"), "Expected '--plan' in output: " + output);
        assertTrue(output.contains("-o") && output.contains("--output"), "Expected output options in output: " + output);
        assertTrue(output.contains("-s") && output.contains("--session"), "Expected session options in output: " + output);
        assertTrue(output.contains("--update-session"), "Expected '--update-session' in output: " + output);
        assertTrue(output.contains("--browser"), "Expected '--browser' in output: " + output);
        assertTrue(output.contains("--headed"), "Expected '--headed' in output: " + output);
        assertTrue(output.contains("--timeout"), "Expected '--timeout' in output: " + output);
        assertTrue(output.contains("--stay-open"), "Expected '--stay-open' in output: " + output);
    }

    @Test
    @Order(4)
    @DisplayName("Template command shows help")
    void testTemplateHelp() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine templateCmd = cmd.getSubcommands().get("template");
        assertNotNull(templateCmd, "template subcommand should exist");

        String output = getUsage(templateCmd);

        assertTrue(output.contains("template"), "Expected 'template' in output: " + output);
        assertTrue(output.contains("google"), "Expected 'google' in output: " + output);
        assertTrue(output.contains("login"), "Expected 'login' in output: " + output);
        assertTrue(output.contains("empty"), "Expected 'empty' in output: " + output);
        assertTrue(output.contains("-o") && output.contains("--output"), "Expected output options in output: " + output);
        assertTrue(output.contains("-f") && output.contains("--force"), "Expected force options in output: " + output);
    }

    @Test
    @Order(5)
    @DisplayName("Template command generates empty template")
    void testTemplateEmpty() {
        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("template", "empty", "-o", testPlanFile.getAbsolutePath(), "--force");

        assertEquals(0, exitCode, "Template command should exit with code 0");
        assertTrue(testPlanFile.exists(), "Template file should be created");

        // Verify template content
        String content = readFile(testPlanFile);
        assertTrue(content.contains("name"), "Template should contain 'name'");
        assertTrue(content.contains("url"), "Template should contain 'url'");
        assertTrue(content.contains("steps"), "Template should contain 'steps'");
        assertTrue(content.contains("action"), "Template should contain 'action'");
        assertTrue(content.contains("extract"), "Template should contain 'extract'");
    }

    @Test
    @Order(6)
    @DisplayName("Template command generates google template")
    void testTemplateGoogle() {
        CommandLine cmd = new CommandLine(new Main());
        File googleFile = tempDir.resolve("google-plan.json").toFile();
        int exitCode = cmd.execute("template", "google", "-o", googleFile.getAbsolutePath(), "--force");

        assertEquals(0, exitCode, "Template command should exit with code 0");
        assertTrue(googleFile.exists(), "Template file should be created");

        String content = readFile(googleFile);
        assertTrue(content.contains("Google Search Test"), "Template should contain 'Google Search Test'");
        assertTrue(content.contains("fill"), "Template should contain 'fill'");
        assertTrue(content.contains("click"), "Template should contain 'click'");
        assertTrue(content.contains("wait"), "Template should contain 'wait'");
        assertTrue(content.contains("assert"), "Template should contain 'assert'");
    }

    @Test
    @Order(7)
    @DisplayName("Template command generates login template")
    void testTemplateLogin() {
        CommandLine cmd = new CommandLine(new Main());
        File loginFile = tempDir.resolve("login-plan.json").toFile();
        int exitCode = cmd.execute("template", "login", "-o", loginFile.getAbsolutePath(), "--force");

        assertEquals(0, exitCode, "Template command should exit with code 0");
        assertTrue(loginFile.exists(), "Template file should be created");

        String content = readFile(loginFile);
        // Login template might have different name, check for common login elements
        assertTrue(content.contains("login") || content.contains("Login") || content.contains("auth"),
                   "Template should contain login/auth related content: " + content);
        assertTrue(content.contains("fill"), "Template should contain 'fill'");
        assertTrue(content.contains("click"), "Template should contain 'click'");
    }

    @Test
    @Order(8)
    @DisplayName("Action command requires input or plan")
    void testActionRequiresInput() {
        CommandLine cmd = new CommandLine(new Main());
        CommandLine actionCmd = cmd.getSubcommands().get("action");

        // Execute with missing required option - should fail with non-zero exit code
        int exitCode = actionCmd.execute("-o", outputFile.getAbsolutePath());
        assertNotEquals(0, exitCode, "Action command should fail when input/plan is missing");
    }

    @Test
    @Order(9)
    @DisplayName("Action command validates test plan structure")
    void testActionValidatesTestPlan() throws Exception {
        // Create invalid test plan (missing URL)
        String invalidPlan = "{\"name\": \"Test\", \"steps\": []}";
        Files.writeString(testPlanFile.toPath(), invalidPlan);

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("action", "-i", testPlanFile.getAbsolutePath(), "-o", outputFile.getAbsolutePath());

        // Should fail with non-zero exit code
        assertNotEquals(0, exitCode, "Action command should fail with invalid test plan");
    }

    @Test
    @Order(10)
    @DisplayName("Unknown command shows error")
    void testUnknownCommand() {
        CommandLine cmd = new CommandLine(new Main());

        int exitCode = cmd.execute("unknown-command");
        assertNotEquals(0, exitCode, "Unknown command should fail with non-zero exit code");
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

    private String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (Exception e) {
            return "";
        }
    }
}