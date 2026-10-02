# web-extract

A Java CLI that opens websites with Playwright, runs queries against the page, and saves the results as JSON. Sites that require a login are supported through a saved session.

## Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first run (Playwright downloads the browser automatically)

## Build

```bash
mvn clean package
alias web-extract='java -jar target/web-extract-1.0.0.jar'
```

## Workflow for sites that need login

1. **Create the session once** with `login`. It saves cookies and local storage to a file.
2. **Reuse it** with `extract --session`.

### Step 1: login

Manual mode (best for 2FA, CAPTCHA, SSO). A browser window opens, you log in, then press Enter in the terminal:

```bash
web-extract login https://example.com/login -s session.json
```

Automatic mode (simple username/password forms). Credentials come from environment variables, or are prompted. They are never passed as command-line arguments, so they stay out of shell history:

```bash
export WEB_EXTRACT_USER="me@example.com"
export WEB_EXTRACT_PASS="secret"

web-extract login https://example.com/login -s session.json \
  --user-selector "#email" \
  --pass-selector "#password" \
  --submit-selector "button[type=submit]" \
  --success-url "**/dashboard"
```

`--success-url` or `--success-selector` tells the tool how to know the login worked. Without either, it waits for the network to go idle.

### Step 2: extract

```bash
web-extract extract https://example.com/account -s session.json \
  -q "h1" -q ".order-row" -o account.json
```

Add `--update-session` to write the refreshed session back to the file after the run (useful when the site rotates tokens).

If every query returns zero matches while a session is used, the tool warns that the session may have expired. Run `login` again.

## Extract options

| Option | Description |
|---|---|
| `-q, --query` | Query to run (repeatable, required). CSS by default; also `xpath=`, `text=`, `role=` |
| `-a, --attr` | Attribute to extract from each match, e.g. `href` (repeatable) |
| `-s, --session` | Session file created by `login` |
| `--update-session` | Save the session back after extracting |
| `-o, --output` | Output JSON file (default `output.json`) |
| `--html` | Include inner HTML of each match |
| `--limit N` | Max matches per query (0 = all) |
| `--wait-for SELECTOR` | Wait for a selector before extracting (JS-heavy pages) |
| `--browser` | `chromium` (default), `firefox`, `webkit` |
| `--headed` | Show the browser window |
| `--timeout MS` | Timeout in ms (default 30000) |
| `--delay MS` | Delay in milliseconds after page load before extracting. |

## Examples

```bash
# Public page, no session
web-extract extract https://example.com -q h1 -q p -o example.json

# All links with their href
web-extract extract https://example.com -q a -a href

# XPath, with HTML included
web-extract extract https://example.com -q "xpath=//h1" --html
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
        { "text": "Order #1001" },
        { "text": "Order #1002" }
      ]
    }
  ]
}
```

If a query is invalid, that entry gets an `"error"` field and the remaining queries still run.

## Security

The session file holds live login cookies, so anyone with it can act as you on that site. Keep it out of version control (a `.gitignore` is included) and delete it when you no longer need it. On Linux and macOS the file is created with owner-only permissions.
