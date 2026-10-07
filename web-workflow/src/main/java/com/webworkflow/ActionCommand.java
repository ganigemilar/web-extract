package com.webworkflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitUntilState;
import com.webcommon.BrowserSupport;
import com.webcommon.extraction.ExtractionResult;
import com.webcommon.extraction.Extractor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
    name = "action",
    mixinStandardHelpOptions = true,
    description = "Execute a sequence of browser automation steps defined in a JSON test plan."
)
public class ActionCommand implements Callable<Integer> {

  private final ObjectMapper objectMapper = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .findAndRegisterModules();
  @Option(names = {"-i", "--input"}, paramLabel = "FILE",
      description = "JSON test plan file (alternative to --plan)")
  private Path inputFile;
  @Option(names = {"--plan"}, paramLabel = "JSON",
      description = "Inline JSON test plan (alternative to -i/--input)")
  private String planJson;
  @Option(names = {"-s", "--session"}, paramLabel = "FILE",
      description = "Initial session file created by the 'login' command (from web-extractor module).")
  private Path session;
  @Option(names = "--update-session",
      description = "Save the session back to the file after all steps (keeps rotating tokens fresh).")
  private boolean updateSession;
  @Option(names = {"-o", "--output"}, defaultValue = "action-result.json", paramLabel = "FILE",
      description = "JSON output file (default: ${DEFAULT-VALUE}).")
  private Path output;
  @Option(names = "--browser", defaultValue = "chromium", paramLabel = "NAME",
      description = "chromium | firefox | webkit (default: ${DEFAULT-VALUE}).")
  private String browserName;
  @Option(names = "--headed", description = "Show the browser window instead of running headless.")
  private boolean headed;
  @Option(names = "--timeout", defaultValue = "30000", paramLabel = "MS",
      description = "Default navigation/wait timeout in milliseconds (default: ${DEFAULT-VALUE}).")
  private long defaultTimeoutMs;
  @Option(names = {"--delay"}, paramLabel = "MS",
      description = "Default delay in milliseconds after each step (default: 0).")
  private long defaultDelayMs = 0;
  @Option(names = {"--stay-open"}, paramLabel = "MS", arity = "0..1",
      description = "Keep browser open after all steps (only with --headed). Optional timeout in ms to auto-close; if omitted, waits for Enter key.")
  private String stayOpen = null;

  @Override
  public Integer call() throws Exception {
    // Parse input JSON
    TestPlan plan = parseInput();
    if (plan == null) {
      return 2;
    }

    // Validate plan
    String validationError = validatePlan(plan);
    if (validationError != null) {
      System.err.println(validationError);
      return 2;
    }

    // CLI overrides for plan settings
    if (session != null) plan.setSession(session);
    if (updateSession) plan.setUpdateSession(true);
    if (browserName != null) plan.setBrowser(browserName);
    if (headed) plan.setHeaded(true);
    if (defaultTimeoutMs > 0) plan.setTimeout(defaultTimeoutMs);
    if (defaultDelayMs > 0) plan.setDelay(defaultDelayMs);
    if (stayOpen != null) plan.setStayOpen(parseStayOpen(stayOpen));

    // Parse --stay-open option
    Long stayOpenTimeoutMs = null;
    boolean stayOpenIndefinite = false;
    if (plan.getStayOpen() != null) {
      if (!plan.isHeaded()) {
        System.err.println("--stay-open requires --headed (browser must be visible)");
        return 2;
      }
      if (plan.getStayOpen() == -1) {
        stayOpenIndefinite = true;
      } else if (plan.getStayOpen() > 0) {
        stayOpenTimeoutMs = plan.getStayOpen();
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    List<StepResult> stepResults = new ArrayList<>();
    boolean overallSuccess = true;
    long totalDurationMs = 0;
    String startUrl = plan.getUrl();
    String finalUrl = startUrl;
    String finalTitle = "";

    try (Playwright playwright = Playwright.create()) {
      BrowserType type = BrowserSupport.type(playwright, plan.getBrowser());

      Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
      if (plan.getSession() != null && Files.isRegularFile(plan.getSession())) {
        contextOptions.setStorageStatePath(plan.getSession());
      }

      Browser browser = type.launch(new BrowserType.LaunchOptions().setHeadless(!plan.isHeaded()));
      BrowserContext context = browser.newContext(contextOptions);

      try {
        Page page = context.newPage();
        page.setDefaultTimeout(plan.getTimeout());

        System.err.println("Opening " + plan.getUrl() + (plan.getSession() != null ? " with session " + plan.getSession() : "") + " ...");
        page.navigate(plan.getUrl(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));

        finalUrl = page.url();
        finalTitle = page.title();

        // Execute each step
        for (int i = 0; i < plan.getSteps().size(); i++) {
          TestStep step = plan.getSteps().get(i);
          StepResult stepResult = new StepResult(step);
          long stepStartTime = System.currentTimeMillis();

          try {
            executeStep(page, step, stepResult);
            stepResult.setSuccess(true);
          } catch (Exception e) {
            stepResult.setSuccess(false);
            stepResult.setError(BrowserSupport.firstLine(e.getMessage()));
            overallSuccess = false;
          }

          long stepDuration = System.currentTimeMillis() - stepStartTime;
          stepResult.setDurationMs(stepDuration);
          totalDurationMs += stepDuration;

          // Update final URL/title after each step
          finalUrl = page.url();
          finalTitle = page.title();
          stepResult.setNewUrl(finalUrl);

          stepResults.add(stepResult);

          // Handle inline assertions
          if (step.getAssertion() != null) {
            List<AssertionResult> assertionResults = executeAssertions(page, step.getAssertion());
            stepResult.setAssertions(assertionResults);
            boolean allPassed = assertionResults.stream().allMatch(AssertionResult::isPassed);
            if (!allPassed) {
              stepResult.setSuccess(false);
              overallSuccess = false;
            }
          }

          // Run extraction queries if specified
          if (step.getExtract() != null && !step.getExtract().isEmpty()) {
            List<ExtractionResult> extractionResults = Extractor.extract(page, step.getExtract());
            // Convert ExtractionResult to Map for JSON serialization
            List<Map<String, Object>> extractMaps = new ArrayList<>();
            for (ExtractionResult er : extractionResults) {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("query", er.getQuery());
              entry.put("count", er.getCount());
              entry.put("matches", er.getMatches());
              if (er.hasError()) {
                entry.put("error", er.getError());
              }
              extractMaps.add(entry);
            }
            stepResult.setExtract(extractMaps);
          }

          // Apply delay after step
          long delay = Math.max(step.getDelay(), plan.getDelay() != 0 ? plan.getDelay() : defaultDelayMs);
          if (delay > 0) {
            page.waitForTimeout(delay);
          }

          // Stop on failure unless continueOnError
          if (!stepResult.isSuccess() && !step.isContinueOnError()) {
            System.err.println("Step " + (i + 1) + " failed: " + stepResult.getError());
            break;
          }
        }

        // Save session if requested
        if (plan.isUpdateSession() && plan.getSession() != null) {
          context.storageState(new BrowserContext.StorageStateOptions().setPath(plan.getSession()));
        }

        // Handle --stay-open
        if (stayOpenIndefinite || stayOpenTimeoutMs != null) {
          if (stayOpenIndefinite) {
            System.err.println("Test completed. Browser staying open. Press Enter to close...");
            try {
              System.in.read();
            } catch (Exception ignored) {
            }
          } else {
            System.err.println("Test completed. Browser staying open for " + stayOpenTimeoutMs + " ms...");
            page.waitForTimeout(stayOpenTimeoutMs);
          }
        }

      } finally {
        context.close();
        browser.close();
      }
    } catch (PlaywrightException e) {
      System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
      overallSuccess = false;
      result.put("error", BrowserSupport.firstLine(e.getMessage()));
      return 1;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      overallSuccess = false;
      result.put("error", e.getMessage());
      return 2;
    }

    // Build final result
    result.put("success", overallSuccess);
    result.put("name", plan.getName());
    result.put("url", finalUrl);
    result.put("title", finalTitle);
    result.put("extractedAt", Instant.now().toString());
    result.put("sessionUsed", plan.getSession() != null);
    result.put("steps", stepResults);
    result.put("summary", Map.of(
        "totalSteps", stepResults.size(),
        "passed", (int) stepResults.stream().filter(StepResult::isSuccess).count(),
        "failed", (int) stepResults.stream().filter(r -> !r.isSuccess()).count(),
        "totalDurationMs", totalDurationMs
    ));

    Path parent = plan.getOutput().toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    objectMapper.writeValue(plan.getOutput().toFile(), result);

    System.err.println("Saved action result to " + plan.getOutput().toAbsolutePath());
    return overallSuccess ? 0 : 1;
  }

  private TestPlan parseInput() throws IOException {
    if (inputFile == null && (planJson == null || planJson.isBlank())) {
      System.err.println("Either -i/--input FILE or --plan JSON must be provided.");
      return null;
    }

    String json;
    if (inputFile != null) {
      if (!Files.isRegularFile(inputFile)) {
        System.err.println("Input file not found: " + inputFile);
        return null;
      }
      json = Files.readString(inputFile);
    } else {
      json = planJson;
    }

    try {
      TestPlan plan = objectMapper.readValue(json, TestPlan.class);
      if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
        System.err.println("Test plan must contain at least one step.");
        return null;
      }
      return plan;
    } catch (Exception e) {
      System.err.println("Failed to parse JSON: " + e.getMessage());
      return null;
    }
  }

  private String validatePlan(TestPlan plan) {
    if (plan.getUrl() == null || plan.getUrl().isBlank()) {
      return "Test plan must include a 'url' field.";
    }
    if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
      return "Test plan must include at least one step in 'steps' array.";
    }
    for (int i = 0; i < plan.getSteps().size(); i++) {
      TestStep step = plan.getSteps().get(i);
      if (step.getAction() == null || step.getAction().isBlank()) {
        return "Step " + (i + 1) + " must have an 'action' field.";
      }
      String action = step.getAction().toLowerCase();
      if (requiresSelector(action) && (step.getSelector() == null || step.getSelector().isBlank())) {
        return "Step " + (i + 1) + " (" + action + ") requires a 'selector' field.";
      }
      if (requiresValue(action) && (step.getValue() == null || step.getValue().isBlank())) {
        return "Step " + (i + 1) + " (" + action + ") requires a 'value' field.";
      }
    }
    return null;
  }

  private boolean requiresSelector(String action) {
    return switch (action.toLowerCase()) {
      case "click", "dblclick", "rightclick", "fill", "type", "press",
           "hover", "select", "check", "uncheck" -> true;
      case "extract" -> false;
      default -> false;
    };
  }

  private boolean requiresValue(String action) {
    return switch (action.toLowerCase()) {
      case "fill", "type", "press", "select", "navigate", "screenshot" -> true;
      case "extract" -> false;
      default -> false;
    };
  }

  private Long parseStayOpen(String value) {
    if (value == null || value.isEmpty()) {
      return -1L; // indefinite
    }
    try {
      long ms = Long.parseLong(value);
      if (ms < 0) {
        throw new IllegalArgumentException("--stay-open timeout must be non-negative");
      }
      return ms;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("--stay-open value must be a number (milliseconds)");
    }
  }

  private void executeStep(Page page, TestStep step, StepResult result) {
    String action = step.getAction().toLowerCase();
    String selector = step.getSelector();
    String value = step.getValue();

    // Wait for selector before action if specified
    if (step.getWaitFor() != null && !step.getWaitFor().isBlank()) {
      page.waitForSelector(step.getWaitFor());
    }

    Locator locator = null;
    if (selector != null && !selector.isBlank()) {
      locator = page.locator(selector);
      result.setMatches(locator.count());
    }

    switch (action) {
      case "click" -> {
        if (locator != null) {
          Locator.ClickOptions options = buildClickOptions(step.getOptions());
          locator.first().click(options);
          result.setNavigated(true);
        }
      }
      case "dblclick" -> {
        if (locator != null) {
          Locator.ClickOptions options = buildClickOptions(step.getOptions());
          options.setClickCount(2);
          locator.first().click(options);
          result.setNavigated(true);
        }
      }
      case "rightclick" -> {
        if (locator != null) {
          Locator.ClickOptions options = buildClickOptions(step.getOptions());
          options.setButton(MouseButton.RIGHT);
          locator.first().click(options);
        }
      }
      case "fill" -> {
        if (locator != null) {
          locator.first().fill(value);
        }
      }
      case "type" -> {
        if (locator != null) {
          // type doesn't have modifiers option, use press for special keys
          locator.first().type(value);
        }
      }
      case "press" -> {
        if (locator != null) {
          locator.first().press(value);
        }
      }
      case "hover" -> {
        if (locator != null) {
          locator.first().hover();
        }
      }
      case "select" -> {
        if (locator != null) {
          locator.first().selectOption(value);
        }
      }
      case "check" -> {
        if (locator != null) {
          locator.first().check();
        }
      }
      case "uncheck" -> {
        if (locator != null) {
          locator.first().uncheck();
        }
      }
      case "wait" -> {
        if (selector != null && !selector.isBlank()) {
          page.waitForSelector(selector);
        } else if (value != null && !value.isBlank()) {
          page.waitForTimeout(Long.parseLong(value));
        } else {
          throw new IllegalArgumentException("wait action requires either 'selector' or 'value' (ms)");
        }
      }
      case "scrolluntil", "infinite-scroll" -> {
        executeScrollUntil(page, step, result);
      }
      case "navigate" -> {
        page.navigate(value, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
        result.setNavigated(true);
      }
      case "reload" -> {
        page.reload();
        result.setNavigated(true);
      }
      case "goback" -> {
        page.goBack();
        result.setNavigated(true);
      }
      case "goforward" -> {
        page.goForward();
        result.setNavigated(true);
      }
      case "screenshot" -> {
        Page.ScreenshotOptions options = new Page.ScreenshotOptions().setPath(Path.of(value));
        if (step.getOptions() != null && step.getOptions().containsKey("fullPage")) {
          options.setFullPage((Boolean) step.getOptions().get("fullPage"));
        }
        page.screenshot(options);
      }
      case "assert" -> {
        // Assertion-only step, handled separately
      }
      case "extract" -> {
        // Extraction-only step, run extraction queries
        if (step.getExtract() != null && !step.getExtract().isEmpty()) {
          List<ExtractionResult> extractionResults = Extractor.extract(page, step.getExtract());
          List<Map<String, Object>> extractMaps = new ArrayList<>();
          for (ExtractionResult er : extractionResults) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("query", er.getQuery());
            entry.put("count", er.getCount());
            entry.put("matches", er.getMatches());
            if (er.hasError()) {
              entry.put("error", er.getError());
            }
            extractMaps.add(entry);
          }
          result.setExtract(extractMaps);
        }
      }
      default -> throw new IllegalArgumentException("Unsupported action: " + action);
    }

    // Handle waitForNavigation for navigation actions
    if (result.isNavigated() && shouldWaitForNavigation(action, step)) {
      try {
        page.waitForLoadState(LoadState.NETWORKIDLE);
      } catch (PlaywrightException ignored) {
        // Navigation might not always trigger network idle
      }
    }
  }

  private void executeScrollUntil(Page page, TestStep step, StepResult result) {
    String selector = step.getSelector();
    Map<String, Object> options = step.getOptions();

    // Configuration with defaults
    long delayMs = getOptionAsLong(options, "delayMs", step.getDelay() > 0 ? step.getDelay() : 1000);
    int maxScrolls = getOptionAsInt(options, "maxScrolls", 100);
    int stopWhenSameCount = getOptionAsInt(options, "stopWhenSameCount", 3);
    long scrollAmount = getOptionAsLong(options, "scrollAmount", 0); // 0 = viewport height

    int sameCountStreak = 0;
    int previousCount = 0;
    int totalScrolls = 0;

    // Get initial count if selector provided
    if (selector != null && !selector.isBlank()) {
      previousCount = page.locator(selector).count();
      result.setMatches(previousCount);
    }

    System.err.println("Starting infinite scroll: maxScrolls=" + maxScrolls + ", delayMs=" + delayMs +
        ", stopWhenSameCount=" + stopWhenSameCount + (selector != null ? ", selector=" + selector : ""));

    for (int i = 0; i < maxScrolls; i++) {
      // Scroll down
      if (scrollAmount > 0) {
        page.evaluate("window.scrollBy(0, " + scrollAmount + ")");
      } else {
        page.evaluate("window.scrollBy(0, window.innerHeight)");
      }

      // Wait for content to load
      page.waitForTimeout(delayMs);
      totalScrolls++;

      // Check if we've reached bottom
      Object atBottomResult = page.evaluate("() => window.innerHeight + window.scrollY >= document.body.scrollHeight - 10");
      boolean atBottom = Boolean.TRUE.equals(atBottomResult);
      if (atBottom) {
        System.err.println("Reached bottom of page after " + totalScrolls + " scrolls");
        break;
      }

      // Check if new content loaded (if selector provided)
      if (selector != null && !selector.isBlank()) {
        int currentCount = page.locator(selector).count();
        result.setMatches(currentCount);

        if (currentCount > previousCount) {
          System.err.println("Scroll " + totalScrolls + ": found " + currentCount + " items (+" + (currentCount - previousCount) + ")");
          previousCount = currentCount;
          sameCountStreak = 0;
        } else {
          sameCountStreak++;
          System.err.println("Scroll " + totalScrolls + ": no new items (" + sameCountStreak + "/" + stopWhenSameCount + ")");
          if (sameCountStreak >= stopWhenSameCount) {
            System.err.println("No new content after " + stopWhenSameCount + " scrolls, stopping");
            break;
          }
        }
      }
    }

    result.setSuccess(true);
  }

  private long getOptionAsLong(Map<String, Object> options, String key, long defaultValue) {
    if (options != null && options.containsKey(key)) {
      Object val = options.get(key);
      if (val instanceof Number) return ((Number) val).longValue();
      try { return Long.parseLong(val.toString()); } catch (Exception ignored) {}
    }
    return defaultValue;
  }

  private int getOptionAsInt(Map<String, Object> options, String key, int defaultValue) {
    if (options != null && options.containsKey(key)) {
      Object val = options.get(key);
      if (val instanceof Number) return ((Number) val).intValue();
      try { return Integer.parseInt(val.toString()); } catch (Exception ignored) {}
    }
    return defaultValue;
  }

  private boolean shouldWaitForNavigation(String action, TestStep step) {
    if (step.getWaitForNavigation() != null) {
      return step.getWaitForNavigation();
    }
    // Default: wait for navigation on click, dblclick, navigate, goback, goforward
    return switch (action) {
      case "click", "dblclick", "navigate", "goback", "goforward" -> true;
      case "extract" -> false;
      default -> false;
    };
  }

  private Locator.ClickOptions buildClickOptions(Map<String, Object> options) {
    Locator.ClickOptions clickOptions = new Locator.ClickOptions();
    if (options != null) {
      applyOptions(clickOptions, options);
    }
    return clickOptions;
  }

  private void applyOptions(Object target, Map<String, Object> options) {
    // Use reflection to set options - simplified for common cases
    try {
      for (Map.Entry<String, Object> entry : options.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        var method = Arrays.stream(target.getClass().getMethods())
            .filter(m -> m.getName().equals(setterName) && m.getParameterCount() == 1)
            .findFirst()
            .orElse(null);
        if (method != null) {
          method.invoke(target, convertValue(value, method.getParameterTypes()[0]));
        }
      }
    } catch (Exception ignored) {
      // Ignore option errors
    }
  }

  private Object convertValue(Object value, Class<?> targetType) {
    if (value == null) return null;
    if (targetType == String.class) return value.toString();
    if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
    if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
    if (targetType == long.class || targetType == Long.class) return Long.parseLong(value.toString());
    if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value.toString());
    if (targetType.isEnum()) return Enum.valueOf((Class<Enum>) targetType, value.toString());
    if (targetType.isArray() && value instanceof List<?> list) {
      Class<?> componentType = targetType.getComponentType();
      Object array = java.lang.reflect.Array.newInstance(componentType, list.size());
      for (int i = 0; i < list.size(); i++) {
        java.lang.reflect.Array.set(array, i, convertValue(list.get(i), componentType));
      }
      return array;
    }
    return value;
  }

  private List<AssertionResult> executeAssertions(Page page, Assertion assertion) {
    List<AssertionResult> results = new ArrayList<>();

    // urlContains
    if (assertion.getUrlContains() != null) {
      String actualUrl = page.url();
      boolean passed = actualUrl.contains(assertion.getUrlContains());
      results.add(new AssertionResult("urlContains", assertion.getUrlContains(), actualUrl, passed,
          passed ? "URL contains expected substring" : "URL does not contain expected substring"));
    }

    // urlEquals
    if (assertion.getUrlEquals() != null) {
      String actualUrl = page.url();
      boolean passed = actualUrl.equals(assertion.getUrlEquals());
      results.add(new AssertionResult("urlEquals", assertion.getUrlEquals(), actualUrl, passed,
          passed ? "URL matches exactly" : "URL does not match"));
    }

    // titleContains
    if (assertion.getTitleContains() != null) {
      String actualTitle = page.title();
      boolean passed = actualTitle.contains(assertion.getTitleContains());
      results.add(new AssertionResult("titleContains", assertion.getTitleContains(), actualTitle, passed,
          passed ? "Title contains expected substring" : "Title does not contain expected substring"));
    }

    // titleEquals
    if (assertion.getTitleEquals() != null) {
      String actualTitle = page.title();
      boolean passed = actualTitle.equals(assertion.getTitleEquals());
      results.add(new AssertionResult("titleEquals", assertion.getTitleEquals(), actualTitle, passed,
          passed ? "Title matches exactly" : "Title does not match"));
    }

    // selectorExists
    if (assertion.getSelectorExists() != null) {
      int count = page.locator(assertion.getSelectorExists()).count();
      boolean passed = count > 0;
      results.add(new AssertionResult("selectorExists", assertion.getSelectorExists(), count, passed,
          passed ? "Element exists" : "Element not found"));
    }

    // selectorNotExists
    if (assertion.getSelectorNotExists() != null) {
      int count = page.locator(assertion.getSelectorNotExists()).count();
      boolean passed = count == 0;
      results.add(new AssertionResult("selectorNotExists", assertion.getSelectorNotExists(), count, passed,
          passed ? "Element does not exist" : "Element unexpectedly exists"));
    }

    // selectorText
    if (assertion.getSelectorText() != null) {
      var sa = assertion.getSelectorText();
      String text = page.locator(sa.getSelector()).first().innerText();
      boolean passed = true;
      if (sa.getEquals() != null) passed = text.equals(sa.getEquals());
      if (passed && sa.getContains() != null) passed = text.contains(sa.getContains());
      results.add(new AssertionResult("selectorText",
          sa.getEquals() != null ? sa.getEquals() : sa.getContains(), text, passed,
          passed ? "Text matches" : "Text does not match"));
    }

    // selectorValue
    if (assertion.getSelectorValue() != null) {
      var sv = assertion.getSelectorValue();
      String val = page.locator(sv.getSelector()).first().inputValue();
      boolean passed = sv.getEquals() != null && val.equals(sv.getEquals());
      results.add(new AssertionResult("selectorValue", sv.getEquals(), val, passed,
          passed ? "Value matches" : "Value does not match"));
    }

    // selectorCount
    if (assertion.getSelectorCount() != null) {
      var sc = assertion.getSelectorCount();
      int count = page.locator(sc.getSelector()).count();
      boolean passed = true;
      if (sc.getEquals() != null) passed = count == sc.getEquals();
      if (passed && sc.getGt() != null) passed = count > sc.getGt();
      if (passed && sc.getLt() != null) passed = count < sc.getLt();
      results.add(new AssertionResult("selectorCount",
          sc.getEquals() != null ? sc.getEquals() : (sc.getGt() != null ? ">" + sc.getGt() : "<" + sc.getLt()),
          count, passed, passed ? "Count matches" : "Count does not match"));
    }

    // jsExpression
    if (assertion.getJsExpression() != null) {
      try {
        Object result = page.evaluate(assertion.getJsExpression());
        boolean passed = Boolean.TRUE.equals(result);
        results.add(new AssertionResult("jsExpression", true, result, passed,
            passed ? "JS expression returned true" : "JS expression returned false"));
      } catch (Exception e) {
        results.add(new AssertionResult("jsExpression", true, "error", false,
            "JS evaluation failed: " + e.getMessage()));
      }
    }

    return results;
  }
}