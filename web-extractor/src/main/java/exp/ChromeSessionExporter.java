package exp;

import com.microsoft.playwright.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Launches a normal (non-automated) Chrome with remote debugging, lets you log in
 * manually (Google button works), then attaches Playwright over CDP and saves the
 * session as storage state JSON: {"cookies":[...],"origins":[...]}
 *
 * Usage: java ChromeSessionExporter [loginUrl] [profileDir] [outputFile] [chromePath]
 * Defaults: https://x.com/login, chrome-debug-profile, state.json, auto-detected Chrome
 */
public class ChromeSessionExporter {

  private static final int PORT = 9222;

  public static void main(String[] args) throws Exception {
    String url = args.length > 0 ? args[0] : "https://x.com/login";
    String profileDir = new File(args.length > 1 ? args[1] : "chrome-debug-profile").getAbsolutePath();
    Path output = Paths.get(args.length > 2 ? args[2] : "state.json");
    String chromePath = args.length > 3 ? args[3] : defaultChromePath();

    // Plain Chrome process: no automation flags, so Google sign-in works.
    Process chrome = new ProcessBuilder(
        chromePath,
        "--remote-debugging-port=" + PORT,
        "--user-data-dir=" + profileDir,
        "--no-first-run",
        url)
        .inheritIO()
        .start();

    try {
      System.out.println("Log in in the Chrome window that opened, then press Enter here to save the session...");
      new Scanner(System.in).nextLine();

      try (Playwright playwright = Playwright.create()) {
        Browser browser = playwright.chromium().connectOverCDP("http://localhost:" + PORT);
        BrowserContext context = browser.contexts().get(0);
        context.storageState(new BrowserContext.StorageStateOptions().setPath(output));
        System.out.println("Session saved to " + output.toAbsolutePath());
      }
    } finally {
      chrome.destroy();
    }
  }

  private static String defaultChromePath() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
//      return "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
      return "C:\\Users\\ganig\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe";
    } else if (os.contains("mac")) {
      return "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    }
    return "google-chrome";
  }
}