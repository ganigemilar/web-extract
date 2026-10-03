package com.webworkflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "template",
    mixinStandardHelpOptions = true,
    description = "Generate a JSON test plan template file."
)
public class TemplateCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "TEMPLATE", description = "Template type: google, login, empty (default: google)")
    private String templateType = "google";

    @Option(names = {"-o", "--output"}, defaultValue = "test-plan.json", paramLabel = "FILE",
        description = "Output JSON file (default: ${DEFAULT-VALUE}).")
    private Path output;

    @Option(names = {"-f", "--force"}, description = "Overwrite existing file without prompting.")
    private boolean force;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Integer call() throws Exception {
        if (Files.exists(output) && !force) {
            System.err.println("File already exists: " + output + ". Use --force to overwrite.");
            return 2;
        }

        Map<String, Object> template = switch (templateType.toLowerCase()) {
            case "google" -> createGoogleTemplate();
            case "login" -> createLoginTemplate();
            case "empty" -> createEmptyTemplate();
            default -> {
                System.err.println("Unknown template type: " + templateType + ". Available: google, login, empty");
                yield null;
            }
        };

        if (template == null) {
            return 2;
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(output.toFile(), template);

        System.err.println("Generated template: " + output.toAbsolutePath());
        return 0;
    }

    private Map<String, Object> createGoogleTemplate() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", "Google Search Test");
        plan.put("url", "https://www.google.com");
        plan.put("browser", "chromium");
        plan.put("headed", false);
        plan.put("timeout", 30000);
        plan.put("delay", 0);
        plan.put("output", "action-result.json");

        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("step", "fill search query");
        step1.put("action", "fill");
        step1.put("selector", "textarea[name='q']");
        step1.put("value", "test keyword");
        step1.put("delay", 500);

        Map<String, Object> step2 = new LinkedHashMap<>();
        step2.put("step", "click search button");
        step2.put("action", "click");
        step2.put("selector", "input[name='btnK']");
        step2.put("waitForNavigation", true);
        step2.put("delay", 1000);

        Map<String, Object> step3 = new LinkedHashMap<>();
        step3.put("step", "wait for results");
        step3.put("action", "wait");
        step3.put("selector", "div#search");
        step3.put("delay", 500);

        Map<String, Object> step4 = new LinkedHashMap<>();
        step4.put("step", "verify results loaded");
        step4.put("action", "assert");
        Map<String, Object> assertObj = new LinkedHashMap<>();
        assertObj.put("urlContains", "search");
        assertObj.put("selectorExists", "div#rso");
        step4.put("assert", assertObj);

        plan.put("steps", Arrays.asList(step1, step2, step3, step4));
        return plan;
    }

    private Map<String, Object> createLoginTemplate() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", "Login Flow Test");
        plan.put("url", "https://example.com/login");
        plan.put("browser", "chromium");
        plan.put("headed", false);
        plan.put("timeout", 30000);
        plan.put("delay", 0);
        plan.put("output", "action-result.json");
        plan.put("session", "session.json");
        plan.put("updateSession", true);

        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("step", "enter username");
        step1.put("action", "fill");
        step1.put("selector", "#username");
        step1.put("value", "your-username");
        step1.put("delay", 300);

        Map<String, Object> step2 = new LinkedHashMap<>();
        step2.put("step", "enter password");
        step2.put("action", "fill");
        step2.put("selector", "#password");
        step2.put("value", "your-password");
        step2.put("delay", 300);

        Map<String, Object> step3 = new LinkedHashMap<>();
        step3.put("step", "click login button");
        step3.put("action", "click");
        step3.put("selector", "button[type=submit]");
        step3.put("waitForNavigation", true);
        step3.put("delay", 2000);

        Map<String, Object> step4 = new LinkedHashMap<>();
        step4.put("step", "wait for dashboard");
        step4.put("action", "wait");
        step4.put("selector", ".dashboard");
        step4.put("delay", 1000);

        Map<String, Object> step5 = new LinkedHashMap<>();
        step5.put("step", "verify login success");
        step5.put("action", "assert");
        Map<String, Object> assertObj = new LinkedHashMap<>();
        assertObj.put("selectorExists", ".user-menu");
        step5.put("assert", assertObj);

        plan.put("steps", Arrays.asList(step1, step2, step3, step4, step5));
        return plan;
    }

    private Map<String, Object> createEmptyTemplate() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", "My Test Plan");
        plan.put("url", "https://example.com");
        plan.put("browser", "chromium");
        plan.put("headed", false);
        plan.put("timeout", 30000);
        plan.put("delay", 0);
        plan.put("output", "action-result.json");
        plan.put("steps", Arrays.asList(
            Map.of(
                "step", "step name",
                "action", "click",
                "selector", "selector",
                "waitForNavigation", true,
                "delay", 500
            )
        ));
        return plan;
    }
}