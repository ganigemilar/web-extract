package com.webextractor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
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
        "WEB_EXTRACT_USER / WEB_EXTRACT_PASS environment variables, or prompted if not set."})
public class LoginCommand implements Callable<Integer> {

  @Parameters(index = "0", paramLabel = "LOGIN_URL", description = "URL of the login page.")
  private String url;

  @Option(names = {"-s", "--session"}, defaultValue = "session.json", paramLabel = "FILE",
      description = "Where to save the session (default: ${DEFAULT-VALUE}).")
  private Path session;

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

  @Option(names = "--timeout", defaultValue = "30000", paramLabel = "MS",
      description = "Timeout in milliseconds (default: ${DEFAULT-VALUE}).")
  private double timeoutMs;

  @Override
  public Integer call() throws Exception {
    boolean auto = userSelector != null || passSelector != null;
    if (auto && (userSelector == null || passSelector == null)) {
      System.err.println("Automatic login needs both --user-selector and --pass-selector.");
      return 2;
    }

    try (Playwright playwright = Playwright.create()) {
      BrowserType type = BrowserSupport.type(playwright, browserName);
      boolean showWindow = !auto || headed;

      try (Browser browser = type.launch(new BrowserType.LaunchOptions().setHeadless(!showWindow));
           BrowserContext context = browser.newContext()) {

        Page page = context.newPage();
        page.setDefaultTimeout(timeoutMs);
        System.err.println("Opening " + url + " ...");
        page.navigate(url);

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
        restrictPermissions(session);
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
