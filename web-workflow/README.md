# web-workflow

A Java CLI that executes browser automation test plans defined in JSON using Playwright.

## Build

```bash
mvn clean package
```

This produces `target/web-workflow-1.0.0-shaded.jar` (executable JAR with dependencies).

## Usage

Create an alias for convenience:

```bash
alias web-workflow='java -jar target/web-workflow-1.0.0-shaded.jar'
```

### Action Command

Execute a sequence of browser automation steps defined in a JSON test plan.

```bash
web-workflow action [options]
```

#### Options

| Option | Description |
|--------|-------------|
| `-i, --input <FILE>` | JSON test plan file (alternative to `--plan`). |
| `--plan <JSON>` | Inline JSON test plan string (alternative to `-i/--input`). |
| `-s, --session <FILE>` | Initial session file created by the `login` command (from web-extractor module). |
| `--update-session` | Save the session back to the file after all steps (keeps rotating tokens fresh). |
| `-o, --output <FILE>` | JSON output file (default: `action-result.json`). |
| `--browser <NAME>` | `chromium` (default), `firefox`, `webkit`. |
| `--headed` | Show the browser window instead of running headless. |
| `--timeout <MS>` | Default navigation/wait timeout in milliseconds (default: 30000). |
| `--delay <MS>` | Default delay in milliseconds after each step (default: 0). |
| `--stay-open [MS]` | Keep browser open after all steps (only with `--headed`). Optional timeout in ms to auto-close; if omitted, waits for Enter key. |
| `-h, --help` | Show help message. |
| `-V, --version` | Print version information. |

#### Test Plan JSON Structure

```json
{
  "name": "Test Name",
  "url": "https://example.com",
  "session": "session.json",
  "updateSession": false,
  "browser": "chromium",
  "headed": false,
  "timeout": 30000,
  "delay": 0,
  "stayOpen": 0,
  "output": "action-result.json",
  "steps": [
    {
      "step": "optional step name",
      "action": "click|fill|type|press|hover|select|check|uncheck|wait|navigate|reload|goback|goforward|screenshot|assert|extract",
      "selector": "xpath=//... or css=... or text=... or role=...",
      "value": "value for fill/type/press/select/navigate/screenshot",
      "waitFor": "selector to wait for before action",
      "waitForNavigation": true,
      "delay": 500,
      "options": { "force": false, "clickCount": 1, "button": "left", "fullPage": false, "exact": false, "modifiers": [] },
      "assert": { "urlContains": "...", "selectorExists": "...", ... },
      "extract": [ { "query": ".item", "attrs": ["href"], "html": false, "limit": 10 } ],
      "continueOnError": false
    }
  ]
}
```

#### Action Types

| Action | Description | Required Fields |
|--------|-------------|-----------------|
| `click` | Click element | `selector` |
| `dblclick` | Double-click element | `selector` |
| `rightclick` | Right-click element | `selector` |
| `fill` | Fill input/textarea | `selector`, `value` |
| `type` | Type character by character | `selector`, `value` |
| `press` | Press single key (Enter, Tab, ArrowDown, etc.) | `selector`, `value` |
| `hover` | Hover over element | `selector` |
| `select` | Select option in `<select>` | `selector`, `value` |
| `check` / `uncheck` | Check/uncheck checkbox/radio | `selector` |
| `wait` | Wait for selector or timeout | `selector` OR `value` (ms) |
| `navigate` | Navigate to URL | `value` (URL) |
| `reload` | Reload current page | (none) |
| `goback` / `goforward` | Browser history navigation | (none) |
| `screenshot` | Take screenshot | `value` (file path) |
| `assert` | Assertion-only step | `assert` object |
| `extract` | Extract data from page | `extract` array |

#### Assertions (inline or standalone `assert` step)

```json
{
  "urlContains": "substring",
  "urlEquals": "full-url",
  "titleContains": "substring",
  "titleEquals": "full-title",
  "selectorExists": "selector",
  "selectorNotExists": "selector",
  "selectorText": { "selector": "...", "equals": "text", "contains": "substring" },
  "selectorValue": { "selector": "...", "equals": "value" },
  "selectorCount": { "selector": "...", "equals": 5, "gt": 3, "lt": 10 },
  "jsExpression": "document.querySelector('...').innerText === 'expected'"
}
```

#### Extraction (inline on any step or standalone `extract` step)

Extract data from the current page using the same query engine as `web-extractor`.

**Inline extraction** (runs after the step's action):
```json
{
  "step": "click and extract results",
  "action": "click",
  "selector": "button.search",
  "waitForNavigation": true,
  "extract": [
    { "query": ".result-item", "attrs": ["href", "data-id"], "html": true, "limit": 10 }
  ]
}
```

**Standalone extraction step** (action: `extract`):
```json
{
  "step": "extract product data",
  "action": "extract",
  "extract": [
    { "query": "h1.product-title", "attrs": [] },
    { "query": ".price", "attrs": ["data-currency"] },
    { "query": "a.product-link", "attrs": ["href"], "limit": 5 }
  ]
}
```

**Extract Query Fields**:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `query` | string | (required) | CSS/XPath/text/role selector (e.g., `.item`, `xpath=//div`, `text=Submit`) |
| `attrs` | string[] | `[]` | Attributes to extract from each match (e.g., `["href", "src", "data-id"]`) |
| `html` | boolean | `false` | Include inner HTML of each match |
| `limit` | integer | `0` | Max matches to return (0 = all) |

**Selector prefixes** (same as web-extractor):
- `css=.class` — CSS selector (default)
- `xpath=//div` — XPath selector
- `text=Login` — Text selector
- `role=button` — ARIA role selector

#### Examples

**Fill search box and click search (Google):**

```json
{
  "name": "Google Search Test",
  "url": "https://www.google.com",
  "steps": [
    {
      "step": "fill search query",
      "action": "fill",
      "selector": "textarea[name='q']",
      "value": "test"
    },
    {
      "step": "click search button",
      "action": "click",
      "selector": "input[name='btnK']",
      "waitForNavigation": true
    }
  ]
}
```

```bash
web-workflow action -i google-search.json
```

**Multi-page flow (login → dashboard → action):**

```json
{
  "name": "Login and Create Item",
  "url": "https://app.example.com/login",
  "steps": [
    { "action": "fill", "selector": "#username", "value": "user@example.com" },
    { "action": "fill", "selector": "#password", "value": "secret" },
    { "action": "click", "selector": "button[type=submit]", "waitForNavigation": true },
    { "action": "wait", "selector": ".dashboard" },
    { "action": "click", "selector": "a[href='/create']", "waitForNavigation": true },
    { "action": "fill", "selector": "#item-name", "value": "New Item" },
    { "action": "click", "selector": "button.save", "waitForNavigation": true },
    { "action": "assert", "assert": { "selectorText": { "selector": ".success", "contains": "created" } } }
  ]
}
```

```bash
web-workflow action -i login-flow.json -s session.json --update-session
```

**Inline JSON (single step):**

```bash
web-workflow action --plan '{"url":"https://example.com","steps":[{"action":"click","selector":"a"}]}'
```

**Extract data after click (inline extraction):**

```json
{
  "name": "Search and Extract",
  "url": "https://example.com/search",
  "steps": [
    {
      "step": "fill search",
      "action": "fill",
      "selector": "input[name='q']",
      "value": "product"
    },
    {
      "step": "click search and extract results",
      "action": "click",
      "selector": "button.search",
      "waitForNavigation": true,
      "extract": [
        { "query": ".product-item", "attrs": ["href", "data-id"], "html": false, "limit": 20 }
      ]
    }
  ]
}
```

```bash
web-workflow action -i search-extract.json
```

**Standalone extraction step:**

```json
{
  "name": "Extract Product Details",
  "url": "https://shop.example.com/product/123",
  "steps": [
    { "action": "wait", "selector": ".product-detail" },
    {
      "action": "extract",
      "extract": [
        { "query": "h1.product-title" },
        { "query": ".price", "attrs": ["data-currency"] },
        { "query": ".description" }
      ]
    }
  ]
}
```

**Debug with headed browser:**

```bash
web-workflow action -i plan.json --headed --stay-open
```

**Keep browser open for 10 seconds after test:**

```bash
web-workflow action -i plan.json --headed --stay-open 10000
```

#### Output

The command outputs a JSON file with the following structure:

```json
{
  "success": true,
  "name": "Test Name",
  "url": "https://example.com/after-navigation",
  "title": "Page Title",
  "extractedAt": "2026-10-03T12:00:00Z",
  "sessionUsed": false,
  "steps": [
    {
      "step": "fill search query",
      "action": "fill",
      "selector": "textarea[name='q']",
      "value": "test",
      "success": true,
      "matches": 1,
      "error": null,
      "durationMs": 63,
      "navigated": false,
      "newUrl": "https://www.google.com/",
      "assertions": null,
      "extract": [
        {
          "query": ".result",
          "count": 5,
          "matches": [
            { "text": "Result 1", "attributes": { "href": "/result/1" } }
          ]
        }
      ],
      "options": null
    },
    {
      "step": "click search button",
      "action": "click",
      "selector": "input[name='btnK']",
      "value": null,
      "success": true,
      "matches": 1,
      "error": null,
      "durationMs": 2156,
      "navigated": true,
      "newUrl": "https://www.google.com/search?q=test",
      "assertions": null,
      "options": null
    }
  ],
  "summary": {
    "totalSteps": 2,
    "passed": 2,
    "failed": 0,
    "totalDurationMs": 2219
  }
}
```

## Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first run (Playwright downloads the browser automatically)

## Notes

- The session file holds live login cookies, so anyone with it can act as you on that site. Keep it out of version control and delete it when you no longer need it.
- On Linux and macOS the session file is created with owner-only permissions.
- Steps execute sequentially in the same browser context. Page navigations (clicks that redirect, explicit navigates) are automatically handled - subsequent steps run on the new page.

### Template Command

Generate a JSON test plan template file to get started quickly.

```bash
web-workflow template [TEMPLATE] [options]
```

#### Options

| Option | Description |
|--------|-------------|
| `TEMPLATE` | Template type: `google`, `login`, `empty` (default: `google`) |
| `-o, --output <FILE>` | Output JSON file (default: `test-plan.json`) |
| `-f, --force` | Overwrite existing file without prompting |

#### Templates

| Template | Description |
|----------|-------------|
| `google` | Google search flow: fill query, click search, wait for results, verify |
| `login` | Login flow: fill username/password, click submit, wait for dashboard, verify |
| `empty` | Minimal template with one click step |

#### Examples

**Generate Google search template:**

```bash
web-workflow template google -o my-search-test.json
```

**Generate login flow template:**

```bash
web-workflow template login -o login-test.json
```

**Generate empty template:**

```bash
web-workflow template empty -o custom-test.json
```

**Overwrite existing file:**

```bash
web-workflow template google -o test-plan.json --force
```

## Related Modules

This module is designed to work alongside the `web-extractor` module, which provides login and data extraction capabilities.