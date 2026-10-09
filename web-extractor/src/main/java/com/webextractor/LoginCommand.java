package com.webextractor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.webcommon.BrowserSupport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "login",
    mixinStandardHelpOptions = true,
    description = {
        "Log in once and save the session (cookies + local storage) to a file.",
        "",
        "Manual mode (default): a browser window opens, you log in yourself (works with 2FA/CAPTCHA),",
        "then press Enter in the terminal to save the session.",
        "",
        "Automatic mode: pass --user-selector and --pass-selector. Credentials are read from the",
        "WEB_EXTRACT_USER / WEB_EXTRACT_PASS environment variables, or prompted if not set.",
        "",
        "Anti-bot avoidance:",
        "  Use --user-data-dir to persist a real browser profile (recommended for Cloudflare, Google OAuth).",
        "  The profile stores cookies, fingerprint, and login state between runs.",
        "  First run: browser opens, you log in manually (solves CAPTCHA/2FA), press Enter.",
        "  Subsequent runs: reuses the profile, often bypassing challenges automatically."})
public class LoginCommand implements Callable<Integer> {

  @Parameters(index = "0", paramLabel = "LOGIN_URL", description = "URL of the login page.")
  private String url;

  @Option(names = {"-s", "--session"}, paramLabel = "FILE",
      description = "Where to save the session (default: UserData/session.json).")
  private Path session = BrowserSupport.getDefaultSessionFile();

  @Option(names = "--user-selector", paramLabel = "SELECTOR",
      description = "Selector of the username/email field (enables automatic mode).")
  private String userSelector;

  @Option(names = "--pass-selector", paramLabel = "SELECTOR",
      description = "Selector of the password field (enables automatic mode).")
  private String passSelector;

  @Option(names = "--submit-selector", paramLabel = "SELECTOR",
      description = "Selector of the submit button. If omitted, Enter is pressed in the password field.")
  private String submitSelector;

  @Option(names = "--success-url", paramLabel = "GLOB",
      description = "Wait until the URL matches this glob after submitting, e.g. **/dashboard.")
  private String successUrl;

  @Option(names = "--success-selector", paramLabel = "SELECTOR",
      description = "Wait until this selector appears after submitting (alternative to --success-url).")
  private String successSelector;

  @Option(names = "--browser", defaultValue = "chromium", paramLabel = "NAME",
      description = "chromium | firefox | webkit (default: ${DEFAULT-VALUE}).")
  private String browserName;

  @Option(names = "--headed",
      description = "Show the window in automatic mode too (manual mode is always headed).")
  private boolean headed;

  @Option(names = "--timeout", defaultValue = "60000", paramLabel = "MS",
      description = "Timeout in milliseconds (default: ${DEFAULT-VALUE}).")
  private double timeoutMs;

  @Option(names = "--user-data-dir", paramLabel = "DIR",
      description = "Persistent browser profile directory. Reuse across runs to avoid Cloudflare/Google challenges. " +
                    "First run: log in manually and solve CAPTCHA. Later runs: often bypasses challenges.")
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

  public Integer call() throws Exception {
    boolean auto = userSelector != null || passSelector != null;
    if (auto && (userSelector == null || passSelector == null)) {
      System.err.println("Automatic login needs both --user-selector and --pass-selector.");
      return 2;
    }

    // External browser mode: use your real browser via CDP
    if (browserExecutable != null && !browserExecutable.isBlank()) {
      return callWithExternalBrowser();
    }

    // Original Playwright browser modes
    try (Playwright playwright = Playwright.create()) {
      BrowserType type = BrowserSupport.type(playwright, browserName);
      boolean showWindow = !auto || headed;

      Browser browser = null;
      BrowserContext context;

      if (userDataDir != null) {
        // Use persistent context - reuses real browser profile, avoids anti-bot detection
        Files.createDirectories(userDataDir);
        System.err.println("Using persistent browser profile: " + userDataDir.toAbsolutePath());
        context = type.launchPersistentContext(userDataDir,
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(!showWindow)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(getStealthArgs()));
        browser = context.browser();
      } else {
        // Ephemeral context (original behavior)
        browser = type.launch(new BrowserType.LaunchOptions()
            .setHeadless(!showWindow)
            .setIgnoreDefaultArgs(List.of("--enable-automation"))
            .setArgs(getStealthArgs()));
        context = browser.newContext();
      }

      try {
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(timeoutMs);
        System.err.println("Opening " + url + " ...");
        page.navigate(url);

        // Wait for Cloudflare challenge if present
        waitForCloudflare(page);

        if (auto) {
          autoLogin(page);
        } else {
          System.err.println("Log in using the browser window, then press Enter here to save the session...");
          new BufferedReader(new InputStreamReader(System.in)).readLine();
        }

        Path parent = session.toAbsolutePath().getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        context.storageState(new BrowserContext.StorageStateOptions().setPath(session));
      } finally {
        if (userDataDir != null) {
          context.close(); // persistent context: close so the profile is flushed to disk
        } else if (browser != null) {
          browser.close();
        }
      }
    } catch (PlaywrightException e) {
      System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
      return 1;
    } catch (IllegalArgumentException | IllegalStateException e) {
      System.err.println(e.getMessage());
      return 2;
    }

    System.err.println("Session saved to " + session.toAbsolutePath()
        + " (treat it like a password: it grants access to your account).");
    return 0;
  }

  /** Login using an external browser (Chrome, Edge, Brave, Firefox, etc.) via CDP */
  private Integer callWithExternalBrowser() throws Exception {
    boolean auto = userSelector != null || passSelector != null;
    ExternalBrowserSupport.BrowserType extType = ExternalBrowserSupport.BrowserType.fromString(browserType);
    String executablePath = browserExecutable;

    // If executable not specified, try to find it
    if (executablePath == null || executablePath.isBlank()) {
      executablePath = ExternalBrowserSupport.findBrowserExecutable(extType);
      System.err.println("Auto-detected " + extType.displayName + ": " + executablePath);
    }

    // Determine user data directory
    Path profileDir = userDataDir != null ? userDataDir : BrowserSupport.getDefaultBrowserProfileDir(extType.executableName);

    ExternalBrowserSupport.LaunchedBrowser launched = null;
    try (Playwright playwright = Playwright.create()) {
      // Launch external browser
      launched = ExternalBrowserSupport.launch(extType, executablePath, profileDir, cdpPort, url);

      // Manual mode: do NOT attach Playwright while the user logs in.
      // Google/SSO popups break when a CDP client is attached (window.opener/postMessage
      // handoff fails -> "Cross-Origin-Opener-Policy ... postMessage" + "Unsupported provider").
      // So wait for the user first, and only connect afterwards to read the session.
      if (!auto) {
        ExternalBrowserSupport.waitForUser("Log in using the browser window, then press Enter here to save the session...");
      }

      // Connect via CDP
      BrowserContext context;
      if (extType == ExternalBrowserSupport.BrowserType.FIREFOX) {
        context = ExternalBrowserSupport.connectFirefox(playwright, cdpPort);
      } else {
        context = ExternalBrowserSupport.connect(playwright, cdpPort);
      }

      try {
        if (auto) {
          // Automatic mode has to drive the page, so it needs Playwright attached.
          Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
          page.setDefaultTimeout(timeoutMs);
          System.err.println("Connected to external browser. Opening " + url + " ...");
          page.navigate(url);

          // Wait for Cloudflare challenge if present
          waitForCloudflare(page);

          autoLogin(page);
        }

        Path parent = session.toAbsolutePath().getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        context.storageState(new BrowserContext.StorageStateOptions().setPath(session));
        restrictPermissions(session);
      } finally {
        // Don't close context - let the external browser process handle it
        // context.close(); // This would close the browser window
      }
    } catch (PlaywrightException e) {
      System.err.println("Playwright error: " + BrowserSupport.firstLine(e.getMessage()));
      return 1;
    } catch (IllegalArgumentException | IllegalStateException e) {
      System.err.println(e.getMessage());
      return 2;
    } finally {
      if (launched != null) {
        System.err.println("Closing external browser...");
        launched.close();
      }
    }

    System.err.println("Session saved to " + session.toAbsolutePath()
        + " (treat it like a password: it grants access to your account).");
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

  /** Wait for Cloudflare challenge to be solved (manual or automatic) */
  private void waitForCloudflare(Page page) {
    try {
      // Check for Cloudflare challenge page
      page.waitForSelector("#challenge-running, .cf-challenge-running, [data-ray], .ray-id",
          new Page.WaitForSelectorOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
      System.err.println("Cloudflare challenge detected. Waiting for you to solve it in the browser...");
      System.err.println("Press Enter here AFTER solving the challenge in the browser window...");
      new BufferedReader(new InputStreamReader(System.in)).readLine();
      // Give extra time for challenge to complete
      page.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (PlaywrightException ignored) {
      // No Cloudflare challenge detected, continue
    } catch (IOException e) {
      System.err.println("Error reading input: " + e.getMessage());
    }
  }

  private void autoLogin(Page page) {
    String user = System.getenv("WEB_EXTRACT_USER");
    char[] pass = System.getenv("WEB_EXTRACT_PASS") != null
        ? System.getenv("WEB_EXTRACT_PASS").toCharArray()
        : null;

    Console console = System.console();
    if (user == null || user.isBlank()) {
      if (console == null) {
        throw new IllegalStateException("Set WEB_EXTRACT_USER (no interactive console available).");
      }
      user = console.readLine("Username: ");
    }
    if (pass == null) {
      if (console == null) {
        throw new IllegalStateException("Set WEB_EXTRACT_PASS (no interactive console available).");
      }
      pass = console.readPassword("Password: ");
    }

    page.fill(userSelector, user);
    page.fill(passSelector, new String(pass));
    java.util.Arrays.fill(pass, '\0');

    if (submitSelector != null) {
      page.click(submitSelector);
    } else {
      page.press(passSelector, "Enter");
    }

    if (successUrl != null) {
      page.waitForURL(successUrl);
    } else if (successSelector != null) {
      page.waitForSelector(successSelector);
    } else {
      page.waitForLoadState(LoadState.NETWORKIDLE);
    }
  }

  private static void restrictPermissions(Path file) {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Non-POSIX file system (e.g. Windows): skip.
    }
  }
}
