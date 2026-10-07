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
    - **Persistent profile mode**: Use `--user-data-dir` to persist a real browser profile directory (recommended for Cloudflare, Google OAuth). First run creates profile and you log in manually; subsequent runs reuse the profile, often bypassing challenges automatically.
    - **External browser mode (CDP)**: Use `--browser-executable` to launch your real installed browser (Chrome, Edge, Brave, Firefox, Vivaldi) with remote debugging and connect via CDP. Completely avoids automation detection since it's your actual browser binary and profile.
    - Cloudflare challenge detection: waits for challenge selectors and prompts user to solve manually
    - Stores session state as JSON file
    - Sets restrictive file permissions on POSIX systems (rw-------)
    - Options: `--submit-selector`, `--success-url`, `--success-selector`, `--browser`, `--headed`, `--timeout`, `--user-data-dir`, `--browser-executable`, `--browser-type`, `--cdp-port`

2. **ExtractCommand.java** - Core extraction functionality
    - Opens webpage with Playwright
    - Supports persistent browser profiles via `--user-data-dir` (reuses cookies/fingerprint to avoid anti-bot detection)
    - **External browser mode (CDP)**: Use `--browser-executable` to connect to your real browser via CDP
    - Waits for optional selector (`--wait-for`)
    - Executes CSS/XPath/text/role queries (repeatable `-q/--query`)
    - Extracts text, attributes (`-a/--attr`), and optionally inner HTML (`--html`)
    - Outputs structured JSON with metadata (url, title, extractedAt, sessionUsed, results)
    - Supports delay after page load (`--delay` option)
    - Can update session file after extraction (`--update-session`)
    - Warns if session may have expired (no matches for any query)
    - Options: `--browser-executable`, `--browser-type`, `--cdp-port` (external browser mode)

### Utilities

- **BrowserSupport.java** - Helper for Playwright browser selection and error message extraction (same as in web-workflow)
- **ExternalBrowserSupport.java** - Cross-platform utility for launching external browsers (Chrome, Edge, Brave, Firefox, Vivaldi, Chromium) via CDP. Includes:
    - `BrowserType` enum with default launch args per browser
    - `findBrowserExecutable()` - auto-detects browser path per OS (Windows/macOS/Linux)
    - `launch()` - starts browser with remote debugging port and user data dir
    - `connect()` / `connectFirefox()` - connects Playwright via CDP
    - `LaunchedBrowser` wrapper with process cleanup

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
    - **Persistent profile**: use `--user-data-dir` to create/reuse browser profile (recommended for Cloudflare/Google OAuth)
    - **External browser (CDP)**: use `--browser-executable` + `--browser-type` + `--cdp-port` + `--user-data-dir` to launch your real browser via CDP (strongest anti-bot avoidance)
2. User runs `extract` with URL, queries, and optional session file
    - **Playwright browser mode** (default):
        - Browser launches and navigates to URL
        - If `--user-data-dir` provided, uses persistent context with real browser profile
        - If session provided, loads storage state
    - **External browser mode (CDP)**:
        - Launches your real browser executable with `--remote-debugging-port`
        - Connects via CDP to the running browser
        - Uses `--user-data-dir` profile for persistence (session file not applied)
    - Waits for optional `--wait-for` selector
    - Optional `--delay` after page load
    - Executes each query, collecting results
    - Outputs JSON to file (default: output.json)

### Anti-Bot Avoidance

Both `login` and `extract` support two modes for avoiding anti-bot detection:

**1. Persistent browser profiles (`--user-data-dir`)**
- **First run**: Creates profile directory, launches headed browser, user logs in manually (solves CAPTCHA/2FA)
- **Subsequent runs**: Reuses the same profile—cookies, localStorage, and browser fingerprint persist
- Cloudflare challenge detection: waits for `#challenge-running`, `.cf-challenge-running`, `[data-ray]`, `.ray-id` selectors and prompts user to solve manually
- Stealth arguments: `--disable-blink-features=AutomationControlled`, `--disable-features=IsolateOrigins,site-per-process,CrossOriginOpenerPolicy`, `--no-first-run`, `--no-default-browser-check`, etc.

**2. External browser mode (CDP) — strongest avoidance**
- Use `--browser-executable` to launch your **real installed browser** (Chrome, Edge, Brave, Firefox, Vivaldi, Chromium) with remote debugging
- Playwright connects via CDP (`connectOverCDP`) — completely avoids automation detection because it's your actual browser binary, profile, and fingerprint
- Supported browsers: `chrome`, `edge`, `brave`, `vivaldi`, `chromium`, `firefox`
- Auto-detection of browser executable if path not specified (searches standard OS locations)
- Profile persists via `--user-data-dir` — cookies, login state, fingerprint maintained across runs
- **Note**: With CDP connection, `--session` file is not applied; use `--user-data-dir` for session persistence instead

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