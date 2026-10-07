package com.webextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.webcommon.BrowserSupport;
import com.webcommon.extraction.ExtractionOptions;
import com.webcommon.extraction.ExtractionResult;
import com.webcommon.extraction.Extractor;
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
import java.io.IOException;

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

  @Option(names = "--user-data-dir", paramLabel = "DIR",
      description = "Persistent browser profile directory (from 'login --user-data-dir'). " +
                    "Reuses cookies/fingerprint to avoid Cloudflare/Google challenges.")
  private Path userDataDir;

  @Option(names = "--browser-executable", paramLabel = "PATH",
      description = "Path to external browser executable (Chrome, Edge, Brave, Firefox, etc.). " +
                    "If set, launches this browser with remote debugging and connects via CDP. " +
                    "Avoids automation detection entirely since it's your real browser.")
  private String browserExecutable;

  @Option(names = "--browser-type", paramLabel = "TYPE",
      description = "Type of external browser: chrome, edge, brave, vivaldi, chromium, firefox (default: chrome). " +
                    "Used to determine default launch args if --browser-executable not specified.")
  private String browserType = "chrome";

  @Option(names = "--cdp-port", defaultValue = "9222", paramLabel = "PORT",
      description = "CDP/remote debugging port for external browser (default: ${DEFAULT-VALUE}).")
  private int cdpPort = 9222;

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

    // External browser mode: use your real browser via CDP
    if (browserExecutable != null && !browserExecutable.isBlank()) {
      return callWithExternalBrowser();
    }

    Map<String, Object> result = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      BrowserType type = BrowserSupport.type(playwright, browserName);

      Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
      if (session != null) {
        contextOptions.setStorageStatePath(session);
      }

      Browser browser = null;
      BrowserContext context;

      if (userDataDir != null) {
        // Use persistent context - reuses real browser profile
        Files.createDirectories(userDataDir);
        System.err.println("Using persistent browser profile: " + userDataDir.toAbsolutePath());
        context = type.launchPersistentContext(userDataDir,
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(!headed)
                .setArgs(getStealthArgs()));
        browser = context.browser();
      } else {
        // Ephemeral context (original behavior)
        browser = type.launch(new BrowserType.LaunchOptions()
            .setHeadless(!headed)
            .setArgs(getStealthArgs()));
        context = browser.newContext(contextOptions);
      }

      try {
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
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

        // Create global extraction options from CLI flags
        ExtractionOptions options = new ExtractionOptions(attrs, includeHtml, limit);

        // Use shared extractor
        List<ExtractionResult> extractionResults = Extractor.extract(page, queries, options);

        // Convert to JSON-compatible format
        List<Map<String, Object>> queryResults = new ArrayList<>();
        for (ExtractionResult er : extractionResults) {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("query", er.getQuery());
          entry.put("count", er.getCount());
          entry.put("matches", er.getMatches());
          if (er.hasError()) {
            entry.put("error", er.getError());
          }
          queryResults.add(entry);
        }
        result.put("results", queryResults);

        if (session != null && queryResults.stream().allMatch(r -> ((Integer) r.get("count")) == 0)) {
          System.err.println("Warning: no matches at all. The session may have expired "
              + "(or the queries are wrong). Re-run 'login' if the page redirected to a login form.");
        }

        if (updateSession) {
          context.storageState(new BrowserContext.StorageStateOptions().setPath(session));
        }
      } finally {
        if (userDataDir == null && browser != null) {
          browser.close();
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

  /** Extract using an external browser (Chrome, Edge, Brave, Firefox, etc.) via CDP */
  private Integer callWithExternalBrowser() throws Exception {
    Map<String, Object> result = new LinkedHashMap<>();
    ExternalBrowserSupport.BrowserType extType = ExternalBrowserSupport.BrowserType.fromString(browserType);
    String executablePath = browserExecutable;

    // If executable not specified, try to find it
    if (executablePath == null || executablePath.isBlank()) {
      executablePath = ExternalBrowserSupport.findBrowserExecutable(extType);
      System.err.println("Auto-detected " + extType.displayName + ": " + executablePath);
    }

    // Determine user data directory
    Path profileDir = userDataDir != null ? userDataDir : Path.of("browser-profile-" + extType.executableName);

    ExternalBrowserSupport.LaunchedBrowser launched = null;
    try (Playwright playwright = Playwright.create()) {
      // Launch external browser
      launched = ExternalBrowserSupport.launch(extType, executablePath, profileDir, cdpPort, url);

      // Connect via CDP
      BrowserContext context;
      if (extType == ExternalBrowserSupport.BrowserType.FIREFOX) {
        context = ExternalBrowserSupport.connectFirefox(playwright, cdpPort);
      } else {
        context = ExternalBrowserSupport.connect(playwright, cdpPort);
      }

      // Load session if provided
      if (session != null && Files.isRegularFile(session)) {
        // Note: CDP connection doesn't support setting storage state directly
        // The session should be loaded in the browser profile itself
        System.err.println("Note: With external browser, use the browser profile (--user-data-dir) for session persistence. --session file is not applied to CDP connection.");
      }

      try {
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(timeoutMs);

        System.err.println("Connected to external browser. Opening " + url + " ...");
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
        result.put("sessionUsed", false);

        // Create global extraction options from CLI flags
        ExtractionOptions options = new ExtractionOptions(attrs, includeHtml, limit);

        // Use shared extractor
        List<ExtractionResult> extractionResults = Extractor.extract(page, queries, options);

        // Convert to JSON-compatible format
        List<Map<String, Object>> queryResults = new ArrayList<>();
        for (ExtractionResult er : extractionResults) {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("query", er.getQuery());
          entry.put("count", er.getCount());
          entry.put("matches", er.getMatches());
          if (er.hasError()) {
            entry.put("error", er.getError());
          }
          queryResults.add(entry);
        }
        result.put("results", queryResults);

        if (queryResults.stream().allMatch(r -> ((Integer) r.get("count")) == 0)) {
          System.err.println("Warning: no matches at all. Check if you're logged in (use --user-data-dir with a profile where you've logged in).");
        }

        // Note: --update-session not supported with external browser CDP connection
        // The profile itself maintains the session
      } finally {
        // Don't close context - let the external browser process handle it
      }
    } catch (PlaywrightException e) {
      System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
      return 1;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 2;
    } finally {
      if (launched != null) {
        System.err.println("Closing external browser...");
        launched.close();
      }
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

  /** Browser launch arguments to reduce fingerprinting and avoid detection */
  private List<String> getStealthArgs() {
    return List.of(
        "--disable-blink-features=AutomationControlled",
        "--disable-features=IsolateOrigins,site-per-process,CrossOriginOpenerPolicy",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-extensions-except",
        "--disable-plugins-discovery",
        "--disable-default-apps",
        "--password-store=basic",
        "--use-mock-keychain"
    );
  }
}