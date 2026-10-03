# web-extract

A multi-module Java CLI project for web automation and data extraction using Playwright.

## Modules

| Module | Description | Artifact |
|--------|-------------|----------|
| **web-extractor** | Extract data from web pages using CSS/XPath/text/role queries. Supports login sessions for authenticated sites. | `web-extractor-1.0.0.jar` |
| **web-workflow** | Execute browser automation test plans defined in JSON. Supports click, fill, navigate, wait, assertions, and more. | `web-workflow-1.0.0-shaded.jar` |

## Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first run (Playwright downloads the browser automatically)

## Build All Modules

```bash
mvn clean package
```

This builds both modules:
- `web-extractor/target/web-extractor-1.0.0.jar`
- `web-workflow/target/web-workflow-1.0.0-shaded.jar`

## Quick Start

### web-extractor — Data Extraction

```bash
# Build
cd web-extractor && mvn clean package

# Create alias
alias web-extract='java -jar target/web-extractor-1.0.0.jar'

# Login (manual mode - handles 2FA/CAPTCHA)
web-extract login https://example.com/login -s session.json

# Extract data
web-extract extract https://example.com/account -s session.json -q "h1" -q ".order-row" -o account.json
```

See [web-extractor/README.md](web-extractor/README.md) for full documentation.

### web-workflow — Browser Automation Test Plans

```bash
# Build
cd web-workflow && mvn clean package

# Create alias
alias web-workflow='java -jar target/web-workflow-1.0.0-shaded.jar'

# Generate a template
web-workflow template google -o my-test.json

# Run the test plan
web-workflow action -i my-test.json
```

See [web-workflow/README.md](web-workflow/README.md) for full documentation.

## Typical Workflow

1. **Login once** with `web-extractor` to create a session file (cookies + local storage)
2. **Reuse session** in either module:
   - `web-extract extract --session session.json` for data extraction
   - `web-workflow action -i plan.json --session session.json` for test automation
3. **Update session** with `--update-session` to keep rotating tokens fresh

## Security

Session files contain live login cookies — treat them like passwords:
- Keep out of version control (`.gitignore` recommended)
- Delete when no longer needed
- On Linux/macOS, files are created with owner-only permissions (`rw-------`)

## Module Details

### web-extractor
- **Commands**: `login`, `extract`
- **Queries**: CSS (default), `xpath=`, `text=`, `role=`
- **Output**: JSON with text, attributes, optional HTML
- **Session**: Manual (interactive) or automatic (env vars) login modes

### web-workflow
- **Commands**: `action`, `template`
- **Actions**: click, fill, type, press, hover, select, check, wait, navigate, reload, goback, goforward, screenshot, assert
- **Assertions**: urlContains, urlEquals, titleContains, titleEquals, selectorExists, selectorNotExists, selectorText, selectorValue, selectorCount, jsExpression
- **Templates**: google (search), login (auth flow), empty (minimal)
- **Debug**: `--headed`, `--stay-open`

## Extending

Both modules use the same core technologies:
- **Playwright** for browser automation
- **Picocli** for CLI
- **Jackson** for JSON
- **Maven Shade Plugin** for executable JARs

Add new functionality by creating new `@Command` classes and registering them in each module's `Main.java`.