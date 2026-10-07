package exp;

import com.microsoft.playwright.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Opens a real Chrome window with a persistent profile, lets you log in manually,
 * then saves the session (cookies incl. HttpOnly + localStorage) as Playwright
 * storage state JSON: {"cookies":[...],"origins":[...]}
 *
 * Usage: java exp.SessionExporter [loginUrl] [profileDir] [outputFile]
 * Defaults: https://x.com/login, browser-profile, state.json
 */
public class SessionExporter {

  public static void main(String[] args) {
    String url = args.length > 0 ? args[0] : "https://x.com/login";
    Path profileDir = Paths.get(args.length > 1 ? args[1] : "browser-profile");
    Path output = Paths.get(args.length > 2 ? args[2] : "state.json");

    try (Playwright playwright = Playwright.create()) {
      BrowserContext context = playwright.chromium().launchPersistentContext(
          profileDir,
          new BrowserType.LaunchPersistentContextOptions()
              .setChannel("chrome")   // use installed Chrome
              .setHeadless(false)     // headed: less likely to be flagged as a bot
              // remove the flag that tells sites the browser is automated
              .setIgnoreDefaultArgs(java.util.List.of("--enable-automation"))
              .setArgs(java.util.List.of("--disable-blink-features=AutomationControlled")));

      Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
      page.navigate(url);

      System.out.println("Log in in the browser window, then press Enter here to save the session...");
      new Scanner(System.in).nextLine();

      context.storageState(new BrowserContext.StorageStateOptions().setPath(output));
      System.out.println("Session saved to " + output.toAbsolutePath());

      context.close();
    }
  }
}