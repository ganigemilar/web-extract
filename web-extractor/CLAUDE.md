# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

web-extractor is a Java CLI tool that opens websites with Playwright, runs queries against the page, and saves the results as JSON. It supports login sessions for sites requiring authentication. Designed to work alongside the `web-workflow` module (in the sibling directory) for browser automation test plans.

## Key Technologies

- **Java 21** (compiled with Maven, release target 17)
- **Playwright** for browser automation
- **Picocli** for command-line interface
- **Jackson** for JSON serialization
- **Maven Shade Plugin** (via parent POM) to produce an executable JAR with dependencies

## Common Commands

### Build

```bash
mvn clean package
```

Produces `target/web-extractor-1.0.0.jar` (executable JAR with dependencies - shaded via parent POM).

### Run the CLI

After building, you can run:

```bash
java -jar target/web-extractor-1.0.0.jar [command] [options]
```

Or create an alias (as suggested in parent README):

```bash
alias web-extract='java -jar target/web-extractor-1.0.0.jar'
```

### Available Commands

- `web-extract` - Shows help and version
- `web-extract login` - Save login session to a file (manual or automatic)
- `web-extract extract` - Extract data from a webpage

### Test

The project currently does not include unit tests. If tests are added, they can be run with:

```bash
mvn test
```

## Code Architecture

### Entry Point

- `Main.java` - Picocli command definition that delegates to subcommands (`LoginCommand`, `ExtractCommand`)

### Subcommands

1. **LoginCommand.java** - Handles saving browser session (cookies + local storage)
    - **Manual mode** (default): Opens headed browser, user logs in interactively (works with 2FA/CAPTCHA), press Enter in terminal to save session
    - **Automatic mode**: Enabled by providing `--user-selector` and `--pass-selector`. Credentials read from `WEB_EXTRACT_USER` / `WEB_EXTRACT_PASS` environment variables, or prompted interactively if not set
    - Stores session state as JSON file
    - Sets restrictive file permissions on POSIX systems (rw-------)
    - Options: `--submit-selector`, `--success-url`, `--success-selector`, `--browser`, `--headed`, `--timeout`

2. **ExtractCommand.java** - Core extraction functionality
    - Opens webpage with Playwright
    - Waits for optional selector (`--wait-for`)
    - Executes CSS/XPath/text/role queries (repeatable `-q/--query`)
    - Extracts text, attributes (`-a/--attr`), and optionally inner HTML (`--html`)
    - Outputs structured JSON with metadata (url, title, extractedAt, sessionUsed, results)
    - Supports delay after page load (`--delay` option)
    - Can update session file after extraction (`--update-session`)
    - Warns if session may have expired (no matches for any query)

### Utilities

- **BrowserSupport.java** - Helper for Playwright browser selection and error message extraction (same as in web-workflow)

### Selector Syntax

Queries support multiple strategies via prefix (Playwright locator syntax):
- `xpath=//...` - XPath selector
- `css=...` - CSS selector (default if no prefix)
- `text=...` - Text selector
- `role=...` - ARIA role selector

### Data Flow

1. User runs `login` to create session file (optional for public sites)
    - Manual: browser opens, user logs in, presses Enter in terminal
    - Automatic: provide selectors and credentials via env vars
2. User runs `extract` with URL, queries, and optional session file
    - Browser launches and navigates to URL
    - If session provided, loads storage state
    - Waits for optional `--wait-for` selector
    - Optional `--delay` after page load
    - Executes each query, collecting results
    - Outputs JSON to file (default: output.json)

### Output Format

```json
{
  "url": "https://example.com/page",
  "title": "Page Title",
  "extractedAt": "2026-10-03T12:00:00Z",
  "sessionUsed": true,
  "results": [
    {
      "query": "h1",
      "count": 1,
      "matches": [
        { "text": "Heading Text", "attributes": { "href": "..." }, "html": "<h1>..." }
      ]
    }
  ]
}
```

## Extending the Project

- Add new query types by modifying the query parsing in `ExtractCommand.runQuery()`
- Add output formats by changing the serialization section in `ExtractCommand.call()`
- Add new subcommands by creating new `@Command` classes and adding them to Main's subcommands array
- Share BrowserSupport with web-workflow module (identical implementation)

## Configuration

- Dependencies and versions managed in parent `pom.xml` (`../pom.xml`)
- Compiler set to Java 21 with release target 17
- Main class configured in properties for shade plugin (parent POM)
- This module has minimal pom.xml - inherits most configuration from parent

## Notes

- The session file holds live login cookies, so anyone with it can act as you on that site. Keep it out of version control and delete it when no longer needed.
- On Linux and macOS the session file is created with owner-only permissions (rw-------).
- Automatic login mode requires both `--user-selector` and `--pass-selector`.
- Credentials for automatic mode: set `WEB_EXTRACT_USER` and `WEB_EXTRACT_PASS` environment variables, or they will be prompted interactively (requires console).
- Multiple queries can be run in a single extract command by repeating `-q/--query`.
- Multiple attributes can be extracted by repeating `-a/--attr`.
- The `--update-session` flag keeps rotating tokens fresh by saving the session after extraction.
- If all queries return zero matches, a warning is printed suggesting the session may have expired.