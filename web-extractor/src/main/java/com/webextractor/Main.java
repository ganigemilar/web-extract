package com.webextractor;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "web-extract",
    mixinStandardHelpOptions = true,
    version = "web-extract 2.0.0",
    description = "Open websites with Playwright, extract page content by query, and save it as JSON.",
    subcommands = {LoginCommand.class, ExtractCommand.class})
public class Main implements Runnable {

  @Override
  public void run() {
    new CommandLine(this).usage(System.out);
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Main()).execute(args));
  }
}
