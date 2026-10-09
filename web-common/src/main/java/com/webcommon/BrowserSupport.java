package com.webcommon;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BrowserSupport {

  private BrowserSupport() {}

  public static BrowserType type(Playwright playwright, String name) {
    return switch (name.toLowerCase()) {
      case "chromium" -> playwright.chromium();
      case "firefox" -> playwright.firefox();
      case "webkit" -> playwright.webkit();
      default -> throw new IllegalArgumentException(
          "Unknown browser '" + name + "' (use chromium, firefox or webkit)");
    };
  }

  public static String firstLine(String message) {
    if (message == null) return "unknown error";
    int nl = message.indexOf('\n');
    return nl < 0 ? message : message.substring(0, nl);
  }

  /**
   * Returns the default UserData directory for storing session files, output files,
   * and browser profiles. Creates the directory if it doesn't exist.
   * Path: {@code <current-working-dir>/UserData}
   */
  public static Path getDefaultUserDataDir() {
    Path userDataDir = Path.of("UserData").toAbsolutePath();
    try {
      Files.createDirectories(userDataDir);
    } catch (Exception ignored) {
      // If creation fails, return the path anyway - the caller will handle errors
    }
    return userDataDir;
  }

  /**
   * Returns the default session file path: {@code <current-working-dir>/UserData/session.json}
   */
  public static Path getDefaultSessionFile() {
    return getDefaultUserDataDir().resolve("session.json");
  }

  /**
   * Returns the default output file path: {@code <current-working-dir>/UserData/output.json}
   */
  public static Path getDefaultOutputFile() {
    return getDefaultUserDataDir().resolve("output.json");
  }

  /**
   * Returns the default browser profile directory for external browser mode:
   * {@code <current-working-dir>/UserData/browser-profile-<browserType>}
   */
  public static Path getDefaultBrowserProfileDir(String browserType) {
    return getDefaultUserDataDir().resolve("browser-profile-" + browserType.toLowerCase());
  }
}