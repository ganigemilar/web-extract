package com.example.webworkflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "action",
    mixinStandardHelpOptions = true,
    description = "Open a page and perform an action (click, submit, fill) on elements matching a selector.")
public class ActionCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "URL", description = "Page to open, e.g. https://example.com")
    private String url;

    @Option(names = {"-q", "--selector"}, required = true, paramLabel = "SELECTOR",
        description = "Selector to match elements. Plain CSS selectors work (button, #id); "
            + "also supports Playwright prefixes: css=, xpath=, text=, role=.")
    private String selector;

    @Option(names = {"-a", "--action"}, required = true, paramLabel = "ACTION",
        description = "Action to perform: click, submit, fill.")
    private String action;

    @Option(names = {"-v", "--value"}, paramLabel = "VALUE",
        description = "Value to fill in (required for fill action).")
    private String value;

    @Option(names = {"-s", "--session"}, paramLabel = "FILE",
        description = "Session file created by the 'login' command (from web-extractor module).")
    private Path session;

    @Option(names = "--update-session",
        description = "Save the session back to the file after the action (keeps rotating tokens fresh).")
    private boolean updateSession;

    @Option(names = {"-o", "--output"}, defaultValue = "action-result.json", paramLabel = "FILE",
        description = "JSON output file (default: ${DEFAULT-VALUE}).")
    private Path output;

    @Option(names = "--wait-for", paramLabel = "SELECTOR",
        description = "Wait for this selector to appear before performing the action (useful for JS-rendered pages).")
    private String waitFor;

    @Option(names = "--browser", defaultValue = "chromium", paramLabel = "NAME",
        description = "chromium | firefox | webkit (default: ${DEFAULT-VALUE}).")
    private String browserName;

    @Option(names = "--headed", description = "Show the browser window instead of running headless.")
    private boolean headed;

    @Option(names = "--timeout", defaultValue = "30000", paramLabel = "MS",
        description = "Navigation/wait timeout in milliseconds (default: ${DEFAULT-VALUE}).")
    private double timeoutMs;

    @Option(names = {"--delay"}, paramLabel = "MS",
        description = "Delay in milliseconds after page load before performing the action.")
    private long delayMs = 0;

    @Override
    public Integer call() throws Exception {
        if (session != null && !Files.isRegularFile(session)) {
            System.err.println("Session file not found: " + session + ". Create it first with the 'login' command.");
            return 2;
        }
        if (updateSession && session == null) {
            System.err.println("--update-session needs --session FILE.");
            return 2;
        }
        if ("fill".equalsIgnoreCase(action) && value == null) {
            System.err.println("--value is required for fill action.");
            return 2;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        try (Playwright playwright = Playwright.create()) {
            BrowserType type = BrowserSupport.type(playwright, browserName);

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
            if (session != null) {
                contextOptions.setStorageStatePath(session);
            }

            try (Browser browser = type.launch(new BrowserType.LaunchOptions().setHeadless(!headed));
                 BrowserContext context = browser.newContext(contextOptions)) {

                Page page = context.newPage();
                page.setDefaultTimeout(timeoutMs);

                System.err.println("Opening " + url + (session != null ? " with session " + session : "") + " ...");
                page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                if (waitFor != null && !waitFor.isBlank()) {
                    page.waitForSelector(waitFor);
                }
                if (delayMs > 0) {
                    page.waitForTimeout(delayMs);
                }

                Locator locator = page.locator(selector);
                int count = locator.count();
                if (count == 0) {
                    System.err.println("No elements found matching selector: " + selector);
                    result.put("success", false);
                    result.put("error", "No elements found");
                } else {
                    System.err.println("Found " + count + " matching element(s). Performing action: " + action);
                    boolean success = true;
                    String error = null;

                    switch (action.toLowerCase()) {
                        case "click":
                            for (int i = 0; i < count; i++) {
                                try {
                                    locator.nth(i).click();
                                } catch (PlaywrightException e) {
                                    success = false;
                                    error = BrowserSupport.firstLine(e.getMessage());
                                    break;
                                }
                            }
                            break;
                        case "submit":
                            // Submit typically works on a form element; we'll try to submit the first matching element that is a form or contains a form.
                            // For simplicity, we'll click on the first element assuming it's a submit button or we can try to submit a form.
                            // We'll just click the first element as a fallback.
                            try {
                                locator.nth(0).click(); // Assuming the selector points to a submit button
                            } catch (PlaywrightException e) {
                                success = false;
                                error = BrowserSupport.firstLine(e.getMessage());
                            }
                            break;
                        case "fill":
                            if (value == null) {
                                success = false;
                                error = "--value is required for fill action.";
                            } else {
                                for (int i = 0; i < count; i++) {
                                    try {
                                        locator.nth(i).fill(value);
                                    } catch (PlaywrightException e) {
                                        success = false;
                                        error = BrowserSupport.firstLine(e.getMessage());
                                        break;
                                    }
                                }
                            }
                            break;
                        default:
                            success = false;
                            error = "Unsupported action: " + action;
                    }

                    result.put("success", success);
                    if (error != null) {
                        result.put("error", error);
                    }
                }

                result.put("url", page.url());
                result.put("title", page.title());
                result.put("actionPerformed", action);
                result.put("selector", selector);
                result.put("matches", count);
                result.put("extractedAt", Instant.now().toString());
                result.put("sessionUsed", session != null);

                if (updateSession) {
                    context.storageState(new BrowserContext.StorageStateOptions().setPath(session));
                }
            }
        } catch (PlaywrightException e) {
            System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
            result.put("success", false);
            result.put("error", BrowserSupport.firstLine(e.getMessage()));
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return 2;
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .writeValue(output.toFile(), result);

        System.err.println("Saved action result to " + output.toAbsolutePath());
        return ((Boolean) result.getOrDefault("success", false)) ? 0 : 1;
    }
}