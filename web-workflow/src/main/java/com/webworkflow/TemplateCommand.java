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
import java.util.List;
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

        TestPlan template = switch (templateType.toLowerCase()) {
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

    private TestPlan createGoogleTemplate() {
        TestPlan plan = new TestPlan();
        plan.setName("Google Search Test");
        plan.setUrl("https://www.google.com");
        plan.setBrowser("chromium");
        plan.setHeaded(false);
        plan.setTimeout(30000);
        plan.setDelay(0);
        plan.setOutput(Path.of("action-result.json"));

        TestStep step1 = new TestStep();
        step1.setStepName("fill search query");
        step1.setAction("fill");
        step1.setSelector("textarea[name='q']");
        step1.setValue("test keyword");
        step1.setDelay(500);

        TestStep step2 = new TestStep();
        step2.setStepName("click search button");
        step2.setAction("click");
        step2.setSelector("input[name='btnK']");
        step2.setWaitForNavigation(true);
        step2.setDelay(1000);

        TestStep step3 = new TestStep();
        step3.setStepName("wait for results");
        step3.setAction("wait");
        step3.setSelector("div#search");
        step3.setDelay(500);

        TestStep step4 = new TestStep();
        step4.setStepName("verify results loaded");
        step4.setAction("assert");
        step4.setDelay(500);

        Assertion assertion = new Assertion();
        assertion.setUrlContains("search");
        assertion.setSelectorExists("div#rso");
        step4.setAssertion(assertion);

        plan.setSteps(List.of(step1, step2, step3, step4));
        return plan;
    }

    private TestPlan createLoginTemplate() {
        TestPlan plan = new TestPlan();
        plan.setName("Login Flow Test");
        plan.setUrl("https://example.com/login");
        plan.setBrowser("chromium");
        plan.setHeaded(false);
        plan.setTimeout(30000);
        plan.setDelay(0);
        plan.setOutput(Path.of("action-result.json"));
        plan.setSession(Path.of("session.json"));
        plan.setUpdateSession(true);

        TestStep step1 = new TestStep();
        step1.setStepName("enter username");
        step1.setAction("fill");
        step1.setSelector("#username");
        step1.setValue("your-username");
        step1.setDelay(300);

        TestStep step2 = new TestStep();
        step2.setStepName("enter password");
        step2.setAction("fill");
        step2.setSelector("#password");
        step2.setValue("your-password");
        step2.setDelay(300);

        TestStep step3 = new TestStep();
        step3.setStepName("click login button");
        step3.setAction("click");
        step3.setSelector("button[type=submit]");
        step3.setWaitForNavigation(true);
        step3.setDelay(2000);

        TestStep step4 = new TestStep();
        step4.setStepName("wait for dashboard");
        step4.setAction("wait");
        step4.setSelector(".dashboard");
        step4.setDelay(1000);

        TestStep step5 = new TestStep();
        step5.setStepName("verify login success");
        step5.setAction("assert");
        step5.setDelay(500);

        Assertion assertion = new Assertion();
        assertion.setSelectorExists(".user-menu");
        step5.setAssertion(assertion);

        plan.setSteps(List.of(step1, step2, step3, step4, step5));
        return plan;
    }

    private TestPlan createEmptyTemplate() {
        TestPlan plan = new TestPlan();
        plan.setName("My Test Plan");
        plan.setUrl("https://example.com");
        plan.setBrowser("chromium");
        plan.setHeaded(false);
        plan.setTimeout(30000);
        plan.setDelay(0);
        plan.setOutput(Path.of("action-result.json"));

        TestStep step = new TestStep();
        step.setStepName("step name");
        step.setAction("click");
        step.setSelector("selector");
        step.setWaitForNavigation(true);
        step.setDelay(500);

        plan.setSteps(List.of(step));
        return plan;
    }
}