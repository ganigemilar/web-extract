# Architecture & Data Flow

## Overview

This project consists of three Maven modules working together for browser automation and web extraction:

```
web-extract (parent POM)
├── web-common        # Shared extraction logic & data models
├── web-extractor     # CLI: login + extract (JSON output)
└── web-workflow      # CLI: JSON test plan execution (browser automation)
```

---

## Module: web-common

**Purpose:** Shared extraction engine, data models, and utilities used by both CLI tools.

### Key Components

| Class | Responsibility |
|-------|----------------|
| `Extractor` | Core extraction logic - runs queries against a Playwright Page |
| `ExtractionOptions` | Global settings (attributes to extract, HTML inclusion, match limit) |
| `ExtractionQuery` | Single query definition (selector, type: css/xpath/text/role) |
| `ExtractionResult` | Results per query (matches, count, errors) |
| `BrowserSupport` | Playwright browser type selection, error message extraction |

### Data Flow

```
Page (Playwright) + Queries + Options
         │
         ▼
   Extractor.extract()
         │
         ▼
List<ExtractionResult>  ──►  JSON serialization (Jackson)
         │
         ▼
Each ExtractionResult:
  - query: String
  - count: int
  - matches: List<Map<String, Object>>
      - text: String
      - attributes: Map<String, String> (if -a/--attr used)
      - html: String (if --html used)
  - error: String (if query failed)
```

### Selector Syntax (Playwright Locator Prefixes)
- `css=.class` — CSS selector (default)
- `xpath=//div` — XPath selector  
- `text=Login` — Text selector
- `role=button` — ARIA role selector

---

## Module: web-extractor

**Purpose:** Standalone CLI for login session capture and data extraction.

### Commands

#### `login` — Save Session
```
┌─────────────────────────────────────────────────────────────┐
│ 1. Parse CLI args (URL, selectors, browser options)        │
│ 2. Launch browser (Playwright or External CDP)             │
│ 3. Navigate to login URL                                    │
│ 4. Wait for Cloudflare challenge if detected               │
│ 5. Manual: user logs in browser, presses Enter in terminal │
│    Automatic: fill selectors from env vars, submit         │
│ 6. Wait for success URL/selector/network idle              │
│ 7. Save storageState (cookies + localStorage) to JSON file │
│ 8. Restrict file permissions (POSIX: rw-------)            │
└─────────────────────────────────────────────────────────────┘
```

#### `extract` — Extract Data
```
┌─────────────────────────────────────────────────────────────┐
│ 1. Parse CLI args (URL, queries, attrs, session, options)  │
│ 2. Launch browser context:                                  │
│    - Playwright mode: browser.newContext(storageState)     │
│    - Persistent profile: launchPersistentContext(userDataDir)│
│    - External CDP: connectOverCDP to real browser          │
│ 3. Navigate to URL, wait for --wait-for selector           │
│ 4. Optional --delay after load                              │
│ 5. Run Extractor.extract(page, queries, options)           │
│ 6. Build result JSON:                                       │
│    {                                                        │
│      "url", "title", "extractedAt", "sessionUsed",         │
│      "results": [ {query, count, matches[], error?} ]      │
│    }                                                        │
│ 7. Write JSON to --output file                              │
│ 8. Optional --update-session: save storageState back       │
└─────────────────────────────────────────────────────────────┘
```

### Browser Modes

| Mode | Launch Method | Anti-Bot Strength | Use Case |
|------|---------------|-------------------|----------|
| **Playwright (default)** | `browserType.launch()` | Medium (stealth args) | General sites |
| **Persistent Profile** | `launchPersistentContext(userDataDir)` | High (real profile) | Cloudflare, Google OAuth |
| **External CDP** | `connectOverCDP(realBrowser)` | **Strongest** (your actual browser) | x.com, strict anti-bot |

### External Browser (CDP) Flow
```
1. ExternalBrowserSupport.findBrowserExecutable(type)  ──► auto-detect path
2. ExternalBrowserSupport.launch(type, exe, profileDir, port, url)
      └─► ProcessBuilder: chrome.exe --remote-debugging-port=9222 --user-data-dir=...
3. Playwright: chromium.connectOverCDP("http://localhost:9222")
4. Get default BrowserContext from connected browser
5. Use context.pages()[0] or newPage() for extraction
6. On close: destroy external browser process
```

---

## Module: web-workflow

**Purpose:** Execute JSON-defined browser automation test plans.

### Commands

#### `template` — Generate Test Plan
```
template empty|google|login  ──►  writes TestPlan JSON to file
```

#### `action` — Execute Test Plan
```
┌─────────────────────────────────────────────────────────────┐
│ 1. Parse TestPlan JSON (file or --plan inline)             │
│ 2. Validate: must have name, url, at least one step        │
│ 3. Launch browser (Playwright, persistent, or CDP)         │
│ 4. Load session storageState if provided                   │
│ 5. Navigate to initial URL                                  │
│ 6. For each TestStep sequentially:                         │
│    a. Wait for step.waitFor selector if specified          │
│    b. Execute action (click, fill, navigate, wait, etc.)   │
│    c. Handle navigation (waitForNetworkIdle if needed)     │
│    d. Run inline assertions (assert field on step)         │
│    e. Run extraction (extract field on step)               │
│    f. Record StepResult (success, duration, matches, etc.) │
│    g. If failed && !continueOnError: stop                  │
│ 7. Build final JSON:                                        │
│    {                                                        │
│      "plan", "status", "startedAt", "completedAt",         │
│      "steps": [StepResult], "summary": {passed, failed}    │
│    }                                                        │
│ 8. Write result to --output file                            │
│ 9. Optional --update-session: save storageState            │
└─────────────────────────────────────────────────────────────┘
```

### TestPlan Data Model

```json
{
  "name": "Test Name",
  "url": "https://example.com",
  "browser": "chromium|firefox|webkit",
  "timeout": 30000,
  "session": "session.json",
  "steps": [
    {
      "step": "description",
      "action": "click|fill|navigate|wait|assert|extract|...",
      "selector": "css=.btn",
      "value": "text to type",
      "waitFor": ".loaded",
      "waitForNavigation": true,
      "continueOnError": false,
      "assert": { "selectorExists": ".success" },
      "extract": [{ "query": ".item", "attrs": ["href"], "html": true, "limit": 10 }]
    }
  ]
}
```

### Supported Actions

| Action | Required Fields | Description |
|--------|----------------|-------------|
| `click` | selector | Click element |
| `fill` | selector, value | Fill input |
| `type` | selector, value | Type char-by-char |
| `press` | selector, value | Press key (Enter, Tab) |
| `navigate` | value (URL) | Go to URL |
| `wait` | selector OR value (ms) | Wait for element or timeout |
| `assert` | assert object | Assertion-only step |
| `extract` | extract array | Extraction-only step |
| `screenshot` | value (path) | Save screenshot |

### Assertions (inline or standalone)
- `urlContains`, `urlEquals`
- `titleContains`, `titleEquals`
- `selectorExists`, `selectorNotExists`
- `selectorText` (equals/contains)
- `selectorValue` (equals)
- `selectorCount` (equals/gt/lt)
- `jsExpression` (arbitrary JS returning boolean)

### Extraction (inline or standalone)
Same query engine as web-extractor:
```json
"extract": [
  { "query": ".result", "attrs": ["href", "data-id"], "html": true, "limit": 5 }
]
```

---

## Shared Utilities

### BrowserSupport (in web-common & web-extractor, duplicated in web-workflow)
```java
// Browser type selection
BrowserType type = BrowserSupport.type(playwright, "chromium|firefox|webkit");

// Clean error messages
String msg = BrowserSupport.firstLine(playwrightException.getMessage());
```

### ExternalBrowserSupport (web-extractor only)
```java
// Auto-detect browser executable
String path = ExternalBrowserSupport.findBrowserExecutable(BrowserType.CHROME);

// Launch with CDP
LaunchedBrowser launched = ExternalBrowserSupport.launch(type, path, profileDir, 9222, url);

// Connect via CDP
BrowserContext ctx = ExternalBrowserSupport.connect(playwright, 9222);
// Firefox uses different protocol
BrowserContext ctx = ExternalBrowserSupport.connectFirefox(playwright, 9222);

// Cleanup
launched.close(); // destroys process
```

---

## Cross-Module Integration

### Using web-extractor session with web-workflow
```bash
# 1. Create session with web-extractor
web-extract login --user-data-dir ./profile https://site.com/login -s session.json

# 2. Use in web-workflow action
web-workflow action -i plan.json -s session.json -o result.json
```

### Using persistent profile with both
```bash
# Both tools reuse same --user-data-dir profile
web-extract login --user-data-dir ./profile https://site.com/login
web-extract extract --user-data-dir ./profile -q "h1" https://site.com/page

web-workflow action -i plan.json --user-data-dir ./profile -o result.json
```

---

## Build & Run

```bash
# Build all modules (produces shaded JARs)
mvn clean package

# Run web-extractor
java -jar web-extractor/target/web-extractor-1.0.0.jar [login|extract] [options]

# Run web-workflow
java -jar web-workflow/target/web-workflow-1.0.0-shaded.jar [action|template] [options]
```

### Aliases (add to shell config)
```bash
alias web-extract='java -jar web-extractor/target/web-extractor-1.0.0.jar'
alias web-workflow='java -jar web-workflow/target/web-workflow-1.0.0-shaded.jar'
```

---

## Key Design Decisions

1. **Shared extraction engine** in web-common avoids duplication
2. **Picocli subcommands** for clean CLI structure
3. **Maven Shade Plugin** for single executable JARs with dependencies
4. **Playwright storageState** for session portability (JSON cookies + origins)
5. **CDP external browser** for maximum anti-bot avoidance (real browser binary)
6. **Persistent profiles** (`--user-data-dir`) for fingerprint reuse across runs
7. **JSON I/O** for test plans, sessions, and results — pipeline-friendly