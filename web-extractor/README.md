# web-extract

A Java CLI that opens websites with Playwright, runs queries against the page, and saves the results as JSON. Sites that
require a login are supported through a saved session.

## Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first run (Playwright downloads the browser automatically)

## Build

```bash
mvn clean package
alias web-extract='java -jar target/web-extract-1.0.0.jar'
```

## Persistent browser profiles (anti-bot avoidance)

Some sites (Cloudflare, Google OAuth, etc.) challenge automated browsers. Using a persistent profile reuses a real
browser's cookies, fingerprint, and login state across runs:

```bash
# First run: creates profile directory, browser opens, you log in manually (solve CAPTCHA/2FA), press Enter
web-extract login --user-data-dir ./browser-profile https://example.com/login

# Later runs: reuses the profile, often bypasses challenges automatically
web-extract extract --user-data-dir ./browser-profile -q "h1" https://example.com/dashboard
```

The `--user-data-dir` works with both `login` and `extract`. On the first run, the profile is created and you log in
interactively (headed browser). On subsequent runs, the tool uses the same profile—cookies, local storage, and
browser fingerprint persist, so Cloudflare/Google often recognize it as a real browser and skip challenges.

You can also combine `--user-data-dir` with a session file (`-s session.json`) for extra persistence:
```bash
# Use both: persistent profile + session file
web-extract login --user-data-dir ./browser-profile -s session.json https://example.com/login
web-extract extract --user-data-dir ./browser-profile -s session.json -q "h1" https://example.com/dashboard
```

## External browser mode (CDP) — strongest anti-bot avoidance

For sites that still detect Playwright's bundled browsers (e.g., x.com Google OAuth "not secure", Cloudflare challenges),
use **your own installed browser** via Chrome DevTools Protocol (CDP). This completely avoids automation detection
because it's literally your real browser—same binary, same profile, same fingerprint.

```bash
# Login using your real Chrome (or Edge, Brave, Firefox, Vivaldi)
web-extract login --browser-executable "/path/to/chrome" --browser-type chrome --cdp-port 9222 \
  --user-data-dir ./my-profile https://x.com/i/flow/login

# Extract using the same profile (you're already logged in)
web-extract extract --browser-executable "/path/to/chrome" --browser-type chrome --cdp-port 9222 \
  --user-data-dir ./my-profile -q "h1" https://x.com/home
```

**How it works:**
1. Tool launches your browser executable with `--remote-debugging-port=9222` and `--user-data-dir=./my-profile`
2. Playwright connects to it via CDP (`playwright.chromium().connectOverCDP("http://localhost:9222")`)
3. Your browser opens, you log in manually (solves CAPTCHA, 2FA, Google OAuth)
4. Profile persists cookies/fingerprint—subsequent runs reuse it, often bypassing challenges entirely

**Options:**
| Option | Description |
|--------|-------------|
| `--browser-executable PATH` | Path to browser executable (auto-detected if omitted) |
| `--browser-type TYPE` | Browser type: `chrome`, `edge`, `brave`, `vivaldi`, `chromium`, `firefox` (default: `chrome`) |
| `--cdp-port PORT` | CDP remote debugging port (default: `9222`) |
| `--user-data-dir DIR` | Profile directory (persists login state, cookies, fingerprint) |

**Auto-detection:** If `--browser-executable` is omitted, the tool searches standard install locations per OS:
- Windows: `%LOCALAPPDATA%`, `ProgramFiles`, `ProgramFiles(x86)`
- macOS: `/Applications`
- Linux: `/usr/bin`, `/snap/bin`

**Note:** With external browser, `--session` file is not applied to the CDP connection. Use `--user-data-dir` for session persistence instead.

## Workflow for sites that need login

1. **Create the session once** with `login`. It saves cookies and local storage to a file.
2. **Reuse it** with `extract --session`.

### Step 1: login

Manual mode (best for 2FA, CAPTCHA, SSO). A browser window opens, you log in, then press Enter in the terminal:

```bash
web-extract login https://example.com/login -s session.json
```

**With persistent profile (recommended for Cloudflare/Google):**
```bash
web-extract login --user-data-dir ./browser-profile https://example.com/login -s session.json
```

Automatic mode (simple username/password forms). Credentials come from environment variables, or are prompted. They are
never passed as command-line arguments, so they stay out of shell history:

```bash
export WEB_EXTRACT_USER="me@example.com"
export WEB_EXTRACT_PASS="secret"

web-extract login https://example.com/login -s session.json \
  --user-selector "#email" \
  --pass-selector "#password" \
  --submit-selector "button[type=submit]" \
  --success-url "**/dashboard"
```

`--success-url` or `--success-selector` tells the tool how to know the login worked. Without either, it waits for the
network to go idle.

If a Cloudflare challenge appears during login, the tool detects it and pauses for you to solve it in the browser window, then press Enter.

### Step 2: extract

```bash
web-extract extract https://example.com/account -s session.json \
  -q "h1" -q ".order-row" -o account.json
```

Add `--update-session` to write the refreshed session back to the file after the run (useful when the site rotates
tokens).

If every query returns zero matches while a session is used, the tool warns that the session may have expired. Run
`login` again.

## Extract options

| Option                   | Description                                                                          |
|--------------------------|--------------------------------------------------------------------------------------|
| `-q, --query`            | Query to run (repeatable, required). CSS by default; also `xpath=`, `text=`, `role=` |
| `-a, --attr`             | Attribute to extract from each match, e.g. `href` (repeatable)                       |
| `-s, --session`          | Session file created by `login`                                                      |
| `--update-session`       | Save the session back after extracting                                               |
| `-o, --output`           | Output JSON file (default `output.json`)                                             |
| `--html`                 | Include inner HTML of each match                                                     |
| `--limit N`              | Max matches per query (0 = all)                                                      |
| `--wait-for SELECTOR`    | Wait for a selector before extracting (JS-heavy pages)                               |
| `--browser`              | `chromium` (default), `firefox`, `webkit`                                            |
| `--headed`               | Show the browser window                                                              |
| `--timeout MS`           | Timeout in ms (default 30000)                                                        |
| `--delay MS`             | Delay in milliseconds after page load before extracting.                             |
| `--user-data-dir DIR`    | Persistent browser profile directory (reuses cookies/fingerprint to avoid Cloudflare/Google challenges) |
| `--browser-executable`   | Path to external browser executable (Chrome, Edge, Brave, Firefox, etc.). Launches with CDP. |
| `--browser-type`         | Type of external browser: chrome, edge, brave, vivaldi, chromium, firefox (default: chrome) |
| `--cdp-port`             | CDP/remote debugging port for external browser (default: 9222)                       |

## Examples

```bash
# Public page, no session
web-extract extract https://example.com -q h1 -q p -o example.json

# All links with their href
web-extract extract https://example.com -q a -a href

# XPath, with HTML included
web-extract extract https://example.com -q "xpath=//h1" --html

# Persistent profile to avoid Cloudflare/Google challenges
# First run: creates ./browser-profile, browser opens, you log in manually (solve CAPTCHA), press Enter
web-extract login --user-data-dir ./browser-profile https://example.com/login

# Subsequent runs: reuses profile, often bypasses challenges automatically
web-extract extract --user-data-dir ./browser-profile -q ".dashboard-item" -o data.json https://example.com/dashboard

# Combine persistent profile with session file for extra persistence
web-extract login --user-data-dir ./browser-profile -s session.json https://example.com/login
web-extract extract --user-data-dir ./browser-profile -s session.json -q "h1" -o account.json https://example.com/account

# External browser mode (strongest anti-bot avoidance)
# Uses your real Chrome/Edge/Brave/Firefox via CDP - completely bypasses automation detection
# First run: opens your browser, you log in manually (Google OAuth works, Cloudflare solved)
web-extract login --browser-executable "C:\path\to\chrome.exe" --browser-type chrome --cdp-port 9222 \
  --user-data-dir ./my-profile https://x.com/i/flow/login

# Subsequent runs: reuses your browser profile, stays logged in
web-extract extract --browser-executable "C:\path\to\chrome.exe" --browser-type chrome --cdp-port 9222 \
  --user-data-dir ./my-profile -q "h1" -o x-home.json https://x.com/home

# Auto-detect browser executable (omit --browser-executable)
web-extract login --browser-type chrome --cdp-port 9222 --user-data-dir ./my-profile https://x.com/i/flow/login
```

## Output format

```json
{
  "url": "https://example.com/account",
  "title": "My account",
  "extractedAt": "2026-10-02T03:00:00Z",
  "sessionUsed": true,
  "results": [
    {
      "query": ".order-row",
      "count": 2,
      "matches": [
        {
          "text": "Order #1001"
        },
        {
          "text": "Order #1002"
        }
      ]
    }
  ]
}
```

If a query is invalid, that entry gets an `"error"` field and the remaining queries still run.

## Security

The session file holds live login cookies, so anyone with it can act as you on that site. Keep it out of version
control (a `.gitignore` is included) and delete it when you no longer need it. On Linux and macOS the file is created
with owner-only permissions.
