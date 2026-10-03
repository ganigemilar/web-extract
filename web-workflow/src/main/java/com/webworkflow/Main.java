package com.webworkflow;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "web-workflow",
    mixinStandardHelpOptions = true,
    version = "web-workflow 1.0.0",
    description = "Perform actions on websites using Playwright.",
    subcommands = {ActionCommand.class})
public class Main implements Runnable {

  @Override
  public void run() {
    new CommandLine(this).usage(System.out);
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Main()).execute(args));
  }
}