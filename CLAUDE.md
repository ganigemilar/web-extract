# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
web-extract is a Java CLI tool that opens websites with Playwright, runs queries against the page, and saves the results as JSON. It supports login sessions for sites requiring authentication.

## Key Technologies
- **Java 17+** (compiled with Maven)
- **Playwright** for browser automation
- **Picocli** for command-line interface
- **Jackson** for JSON serialization
- **Maven Shade Plugin** to produce an executable JAR with dependencies

## Common Commands

### Build
```bash
mvn clean package
```
Produces `target/web-extract-1.0.0.jar`

### Run the CLI
After building, you can run:
```bash
java -jar target/web-extract-1.0.0.jar [command] [options]
```
Or create an alias (as suggested in README):
```bash
alias web-extract='java -jar target/web-extract-1.0.0.jar'
```

### Available Commands
- `web-extract` - Shows help and version
- `web-extract login` - Save login session to a file
- `web-extract extract` - Extract data from a webpage

### Test
The project currently does not include unit tests. If tests are added, they can be run with:
```bash
mvn test
```

## Code Architecture

### Entry Point
- `Main.java` - Picocli command definition that delegates to subcommands

### Subcommands
1. **LoginCommand.java** - Handles saving browser session (cookies + local storage)
   - Supports manual login (interactive) and automatic login via environment variables
   - Stores session state as JSON file
   - Sets restrictive file permissions on POSIX systems

2. **ExtractCommand.java** - Core extraction functionality
   - Opens webpage with Playwright
   - Waits for optional selector (`--wait-for`)
   - Executes CSS/XPath/text/role queries
   - Extracts text, attributes, and optionally inner HTML
   - Outputs structured JSON with metadata
   - Supports delay after page load (`--delay` option)
   - Can update session file after extraction (`--update-session`)

### Utilities
- **BrowserSupport.java** - Helper for Playwright browser selection and error message extraction

### Data Flow
1. User runs `login` to create session file (optional for public sites)
2. User runs `extract` with URL, queries, and optional session file
   - Browser launches and navigates to URL
   - If session provided, loads storage state
   - Waits for optional selector
   - Executes each query, collecting results
   - Outputs JSON to file (default: output.json)

## Extending the Project
- Add new query types by modifying the query parsing in `ExtractCommand.runQuery()`
- Add output formats by changing the serialization section
- Add new subcommands by creating new `@Command` classes and adding them to Main's subcommands array

## Configuration
- Dependencies and versions managed in `pom.xml`
- Compiler set to Java 17 (though source notes require JDK 17+)
- Main class configured in properties for shade plugin
