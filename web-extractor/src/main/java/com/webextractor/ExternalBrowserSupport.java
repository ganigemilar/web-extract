package com.webextractor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Support for launching an external browser (Chrome, Edge, Firefox, Brave, etc.)
 * with remote debugging and connecting Playwright via CDP.
 * This avoids automation detection since it's a real user browser.
 */
public class ExternalBrowserSupport {

  /** Result of launching an external browser */
  public static class LaunchedBrowser {
    public final Process process;
    public final int cdpPort;
    public final String browserName;

    public LaunchedBrowser(Process process, int cdpPort, String browserName) {
      this.process = process;
      this.cdpPort = cdpPort;
      this.browserName = browserName;
    }

    public void close() {
      if (process != null && process.isAlive()) {
        process.destroy();
        try {
          if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          process.destroyForcibly();
        }
      }
    }
  }

  /** Known browser configurations */
  public enum BrowserType {
    CHROME("chrome", "Google Chrome", new String[]{
        "--remote-debugging-port={port}",
        "--user-data-dir={userDataDir}",
        "--no-first-run",
        "--no-default-browser-check"
    }),
    EDGE("msedge", "Microsoft Edge", new String[]{
        "--remote-debugging-port={port}",
        "--user-data-dir={userDataDir}",
        "--no-first-run",
        "--no-default-browser-check"
    }),
    BRAVE("brave", "Brave Browser", new String[]{
        "--remote-debugging-port={port}",
        "--user-data-dir={userDataDir}",
        "--no-first-run",
        "--no-default-browser-check"
    }),
    VIVALDI("vivaldi", "Vivaldi", new String[]{
        "--remote-debugging-port={port}",
        "--user-data-dir={userDataDir}",
        "--no-first-run",
        "--no-default-browser-check"
    }),
    CHROMIUM("chromium", "Chromium", new String[]{
        "--remote-debugging-port={port}",
        "--user-data-dir={userDataDir}",
        "--no-first-run",
        "--no-default-browser-check"
    }),
    FIREFOX("firefox", "Firefox", new String[]{
        "--start-debugger-server", "{port}",
        "-profile", "{userDataDir}",
        "--no-remote"
    });

    public final String executableName;
    public final String displayName;
    public final String[] defaultArgs;

    BrowserType(String executableName, String displayName, String[] defaultArgs) {
      this.executableName = executableName;
      this.displayName = displayName;
      this.defaultArgs = defaultArgs;
    }

    public static BrowserType fromString(String name) {
      for (BrowserType bt : values()) {
        if (bt.executableName.equalsIgnoreCase(name) || bt.name().equalsIgnoreCase(name)) {
          return bt;
        }
      }
      return CHROME; // default
    }
  }

  /** Find browser executable path */
  public static String findBrowserExecutable(BrowserType type) {
    String os = System.getProperty("os.name").toLowerCase();
    List<String> candidates = new ArrayList<>();

    if (os.contains("win")) {
      String userHome = System.getProperty("user.home");
      String programFiles = System.getenv("ProgramFiles");
      String programFilesX86 = System.getenv("ProgramFiles(x86)");
      String localAppData = System.getenv("LOCALAPPDATA");

      switch (type) {
        case CHROME:
          candidates.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
          candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
          candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
          break;
        case EDGE:
          candidates.add(localAppData + "\\Microsoft\\Edge\\Application\\msedge.exe");
          candidates.add(programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
          candidates.add(programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
          break;
        case BRAVE:
          candidates.add(localAppData + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
          candidates.add(programFiles + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
          break;
        case VIVALDI:
          candidates.add(localAppData + "\\Vivaldi\\Application\\vivaldi.exe");
          candidates.add(programFiles + "\\Vivaldi\\Application\\vivaldi.exe");
          break;
        case FIREFOX:
          candidates.add(programFiles + "\\Mozilla Firefox\\firefox.exe");
          candidates.add(programFilesX86 + "\\Mozilla Firefox\\firefox.exe");
          candidates.add(localAppData + "\\Mozilla Firefox\\firefox.exe");
          break;
      }
    } else if (os.contains("mac")) {
      switch (type) {
        case CHROME:
          candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
          break;
        case EDGE:
          candidates.add("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
          break;
        case BRAVE:
          candidates.add("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser");
          break;
        case VIVALDI:
          candidates.add("/Applications/Vivaldi.app/Contents/MacOS/Vivaldi");
          break;
        case FIREFOX:
          candidates.add("/Applications/Firefox.app/Contents/MacOS/firefox");
          break;
      }
    } else { // Linux
      candidates.add("/usr/bin/" + type.executableName);
      candidates.add("/usr/bin/" + type.executableName + "-browser");
      candidates.add("/snap/bin/" + type.executableName);
    }

    for (String c : candidates) {
      if (Files.isExecutable(Path.of(c))) {
        return c;
      }
    }
    // Fallback: try PATH
    return type.executableName;
  }

  /** Launch external browser with CDP/remote debugging */
  public static LaunchedBrowser launch(BrowserType type, String executablePath, Path userDataDir, int cdpPort, String url) throws IOException {
    Files.createDirectories(userDataDir);

    List<String> cmd = new ArrayList<>();
    cmd.add(executablePath);

    for (String arg : type.defaultArgs) {
      String replaced = arg
          .replace("{port}", String.valueOf(cdpPort))
          .replace("{userDataDir}", userDataDir.toAbsolutePath().toString());
      cmd.add(replaced);
    }

    if (url != null && !url.isBlank()) {
      cmd.add(url);
    }

    System.err.println("Launching " + type.displayName + " on port " + cdpPort + "...");
    System.err.println("Profile: " + userDataDir.toAbsolutePath());
    System.err.println("Command: " + String.join(" ", cmd));

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.inheritIO();
    Process process = pb.start();

    // Wait a bit for browser to start and CDP to be ready
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return new LaunchedBrowser(process, cdpPort, type.displayName);
  }

  /** Connect Playwright to the external browser via CDP */
  public static BrowserContext connect(Playwright playwright, int cdpPort) {
    Browser browser = playwright.chromium().connectOverCDP("http://localhost:" + cdpPort);
    // Get the default context (first one)
    if (browser.contexts().isEmpty()) {
      throw new IllegalStateException("No browser contexts found. Did the browser start correctly?");
    }
    return browser.contexts().get(0);
  }

  /** Firefox uses a different protocol - connect via WebDriver BiDi or CDP if enabled */
  public static BrowserContext connectFirefox(Playwright playwright, int cdpPort) {
    // Firefox can be connected via CDP if started with --start-debugger-server
    // But it's more reliable to use Playwright's Firefox directly with a persistent profile
    // For now, fall back to chromium.connectOverCDP which works if Firefox CDP is enabled
    return connect(playwright, cdpPort);
  }

  /** Interactive wait for user to complete login */
  public static void waitForUser(String message) {
    System.err.println(message);
    System.err.println("Press Enter here AFTER completing the action in the browser window...");
    new Scanner(System.in).nextLine();
  }
}