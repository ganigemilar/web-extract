# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

web-workflow is a Java CLI tool that executes browser automation test plans defined in JSON using Playwright. It supports actions like click, fill, type, navigate, wait, assertions, and more. Designed to work alongside the `web-extractor` module (in the parent directory) for login and data extraction capabilities.

## Key Technologies

- **Java 21** (compiled with Maven, release target 17)
- **Playwright** for browser automation
- **Picocli** for command-line interface
- **Jackson** for JSON serialization/deserialization
- **Maven Shade Plugin** to produce an executable JAR with dependencies

## Common Commands

### Build

```bash
mvn clean package
```

Produces `target/web-workflow-1.0.0-shaded.jar` (executable JAR with dependencies).

### Run the CLI

After building, you can run:

```bash
java -jar target/web-workflow-1.0.0-shaded.jar [command] [options]
```

Or create an alias (as suggested in README):

```bash
alias web-workflow='java -jar target/web-workflow-1.0.0-shaded.jar'
```

### Available Commands

- `web-workflow` - Shows help and version
- `web-workflow action` - Execute a sequence of browser automation steps from a JSON test plan
- `web-workflow template` - Generate a JSON test plan template file

### Test

The project currently does not include unit tests. If tests are added, they can be run with:

```bash
mvn test
```

## Code Architecture

### Entry Point

- `Main.java` - Picocli command definition that delegates to subcommands (`ActionCommand`, `TemplateCommand`)

### Subcommands

1. **ActionCommand.java** - Core execution functionality
    - Parses JSON test plan from file (`-i/--input`) or inline string (`--plan`)
    - Launches Playwright browser (chromium, firefox, webkit)
    - Loads session state from file (cookies + local storage) if provided
    - Executes steps sequentially in the same browser context
    - Handles page navigations automatically (subsequent steps run on new page)
    - Supports inline assertions per step or standalone `assert` steps
    - Outputs structured JSON with step-by-step results and summary
    - Can update session file after extraction (`--update-session`)
    - Debug options: `--headed` to show browser, `--stay-open` to keep browser open

2. **TemplateCommand.java** - Generates JSON test plan templates
    - Templates: `google` (search flow), `login` (auth flow), `empty` (minimal)

### Data Models

- **TestPlan.java** - Root test plan configuration (name, url, browser, timeout, steps, session, etc.)
- **TestStep.java** - Individual step with action, selector, value, waitFor, assertions, options, delay, continueOnError, extract
- **ExtractQuery.java** - Extraction query definition (query, attrs, html, limit)
- **Assertion.java** - Assertion definitions (urlContains, selectorExists, selectorText, selectorValue, selectorCount, jsExpression, etc.)
- **StepResult.java** - Execution result per step (success, matches, error, duration, navigated, newUrl, assertions, extract)
- **AssertionResult.java** - Individual assertion result (type, expected, actual, passed, message)

### Utilities

- **BrowserSupport.java** - Helper for Playwright browser type selection and error message extraction

### Selector Syntax

Selectors support multiple strategies via prefix:
- `xpath=//...` - XPath selector
- `css=...` - CSS selector (default if no prefix)
- `text=...` - Text selector
- `role=...` - ARIA role selector

### Action Types

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

### Assertions

Can be inline on any step (`assert` field) or as standalone `assert` action:
- `urlContains`, `urlEquals`
- `titleContains`, `titleEquals`
- `selectorExists`, `selectorNotExists`
- `selectorText` with `equals` and/or `contains`
- `selectorValue` with `equals`
- `selectorCount` with `equals`, `gt`, `lt`
- `jsExpression` - arbitrary JavaScript returning boolean

### Extraction

Extract data from the current page using the same query engine as `web-extractor`. Can be used inline on any step (runs after the step's action) or as a standalone `extract` action step.

**ExtractQuery fields** (in `extract` array on step or `extract` action):
- `query` (string, required) - CSS/XPath/text/role selector
- `attrs` (string[], default: []) - Attributes to extract (e.g., `["href", "src", "data-id"]`)
- `html` (boolean, default: false) - Include inner HTML of each match
- `limit` (int, default: 0) - Max matches to return (0 = all)

**Selector prefixes** (same as web-extractor):
- `css=.class` — CSS selector (default)
- `xpath=//div` — XPath selector
- `text=Login` — Text selector
- `role=button` — ARIA role selector

**Inline extraction example**:
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

**Standalone extraction step example**:
```json
{
  "step": "extract product data",
  "action": "extract",
  "extract": [
    { "query": "h1.product-title" },
    { "query": ".price", "attrs": ["data-currency"] },
    { "query": "a.product-link", "attrs": ["href"], "limit": 5 }
  ]
}
```

**Extraction output** (added to step result):
```json
{
  "extract": [
    {
      "query": ".result-item",
      "count": 5,
      "matches": [
        { "text": "Item 1", "attributes": { "href": "/item/1", "data-id": "1" }, "html": "<div>..." }
      ]
    }
  ]
}
```

### Data Flow

1. User creates test plan JSON (manually or via `template` command)
2. User optionally runs `login` from web-extractor module to create session file
3. User runs `action` with test plan and optional session file
    - Browser launches and navigates to initial URL
    - If session provided, loads storage state
    - Executes each step sequentially
    - Handles navigations automatically
    - Runs inline assertions
    - Outputs JSON result file with detailed step results

## Extending the Project

- Add new action types in `ActionCommand.executeStep()` switch statement
- Add new assertion types in `Assertion` class and `ActionCommand.executeAssertions()`
- Add new extraction features in `ExtractQuery` class and `ActionCommand.runExtraction()`
- Add new output formats by modifying the result serialization in `ActionCommand.call()`
- Add new subcommands by creating new `@Command` classes and adding them to `Main`'s subcommands array

## Configuration

- Dependencies and versions managed in `pom.xml` (parent POM in `../pom.xml`)
- Compiler set to Java 21 with release target 17
- Main class configured in properties for shade plugin
- Shade plugin filters out signing artifacts (META-INF/*.SF, *.DSA, *.RSA)

## Notes

- The session file holds live login cookies, so anyone with it can act as you on that site. Keep it out of version control and delete it when no longer needed.
- On Linux and macOS the session file is created with owner-only permissions.
- Steps execute sequentially in the same browser context. Page navigations (clicks that redirect, explicit navigates) are automatically handled - subsequent steps run on the new page.
- The `continueOnError` flag allows a test to continue past a failed step.
- The `waitFor` field on steps waits for a selector before executing the action.
- The `waitForNavigation` flag (default true for click/dblclick/navigate/goback/goforward) waits for network idle after navigation actions.