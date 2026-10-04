package com.webcommon;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

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
}