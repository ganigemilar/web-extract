package com.example.webextract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "extract",
        mixinStandardHelpOptions = true,
        description = "Open a page (optionally logged in via a saved session), run queries, and save the result as JSON.")
public class ExtractCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "URL", description = "Page to open, e.g. https://example.com")
    private String url;

    @Option(names = {"-q", "--query"}, required = true, paramLabel = "QUERY",
            description = "Query to run on the page. Repeatable. Plain CSS selectors work (h1, a.link); "
                    + "also supports Playwright prefixes: css=, xpath=, text=, role=.")
    private List<String> queries;

    @Option(names = {"-a", "--attr"}, paramLabel = "ATTR",
            description = "Attribute to extract from each match (e.g. href, src). Repeatable.")
    private List<String> attrs = new ArrayList<>();

    @Option(names = {"-s", "--session"}, paramLabel = "FILE",
            description = "Session file created by the 'login' command.")
    private Path session;

    @Option(names = "--update-session",
            description = "Save the session back to the file after extracting (keeps rotating tokens fresh).")
    private boolean updateSession;

    @Option(names = {"-o", "--output"}, defaultValue = "output.json", paramLabel = "FILE",
            description = "JSON output file (default: ${DEFAULT-VALUE}).")
    private Path output;

    @Option(names = "--html", description = "Also include the inner HTML of each match.")
    private boolean includeHtml;

    @Option(names = "--limit", defaultValue = "0", paramLabel = "N",
            description = "Max matches per query, 0 = no limit (default: ${DEFAULT-VALUE}).")
    private int limit;

    @Option(names = "--wait-for", paramLabel = "SELECTOR",
            description = "Wait for this selector to appear before extracting (useful for JS-rendered pages).")
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
            description = "Delay in milliseconds after page load before extracting.")
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

                result.put("url", page.url());
                result.put("title", page.title());
                result.put("extractedAt", Instant.now().toString());
                result.put("sessionUsed", session != null);

                List<Map<String, Object>> queryResults = new ArrayList<>();
                for (String query : queries) {
                    queryResults.add(runQuery(page, query));
                }
                result.put("results", queryResults);

                if (session != null && queryResults.stream().allMatch(r -> ((Integer) r.get("count")) == 0)) {
                    System.err.println("Warning: no matches at all. The session may have expired "
                            + "(or the queries are wrong). Re-run 'login' if the page redirected to a login form.");
                }

                if (updateSession) {
                    context.storageState(new BrowserContext.StorageStateOptions().setPath(session));
                }
            }
        } catch (PlaywrightException e) {
            System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.toFile(), result);

        System.err.println("Saved results to " + output.toAbsolutePath());
        return 0;
    }

    private Map<String, Object> runQuery(Page page, String query) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("query", query);

        try {
            Locator locator = page.locator(query);
            int total = locator.count();
            int take = limit > 0 ? Math.min(total, limit) : total;

            List<Map<String, Object>> matches = new ArrayList<>();
            for (int i = 0; i < take; i++) {
                Locator el = locator.nth(i);
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("text", el.innerText().trim());

                if (!attrs.isEmpty()) {
                    Map<String, String> attrValues = new LinkedHashMap<>();
                    for (String attr : attrs) {
                        attrValues.put(attr, el.getAttribute(attr));
                    }
                    match.put("attributes", attrValues);
                }
                if (includeHtml) {
                    match.put("html", el.innerHTML());
                }
                matches.add(match);
            }

            entry.put("count", total);
            entry.put("matches", matches);
        } catch (PlaywrightException e) {
            // Bad selector or element detached: record the error and keep going with other queries.
            entry.put("count", 0);
            entry.put("matches", List.of());
            entry.put("error", BrowserSupport.firstLine(e.getMessage()));
        }
        return entry;
    }
}
