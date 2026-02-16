# Quick Start Guide

## Prerequisites

- Java 17+ (`java -version`)
- Maven 3.6+ (`mvn -version`)
- Chrome, Firefox, or Edge browser installed

## Setup (3 Steps)

### Step 1: Install dependencies
```bash
mvn clean install -DskipTests
```

### Step 2: Configure AI provider

Edit `.env` in the project root (pick ONE):

```bash
# Option A: Gemini (recommended - fastest)
GEMINI_API_KEY=your_gemini_key_here

# Option B: OpenAI
OPENAI_API_KEY=sk-your-openai-key-here

# Option C: Claude
CLAUDE_API_KEY=sk-ant-your-claude-key-here
```

No API key? Tests still run -- AI healing is just skipped.

### Step 3: Run tests
```bash
mvn test
```

## What runs

The test suite (`testng.xml`) executes:

| Test | What It Does |
|------|-------------|
| `HealingDemoTest` | Uses **broken selectors** -- watch the AI fix them live |
| `LoginTest` | Valid + invalid login with correct selectors |
| `CheckboxTest` | Toggle checkboxes |
| `DropdownTest` | Select dropdown options |

## Check results

| Output | Location |
|--------|----------|
| AI Healing Report | `target/surefire-reports/ai-healing-report.html` |
| TestNG Report | `target/surefire-reports/index.html` |
| Healing Audit Log | `logs/ai-healing-audit.log` |
| Screenshots | `logs/screenshots/` |

## Configure browser

Edit `src/main/resources/config.properties`:
```properties
browser.type=chrome        # chrome, firefox, edge
browser.headless=false     # true for CI / no window
```

## Configure healing

Edit `src/main/resources/healing-config.yaml`:
```yaml
healing:
  enabled: true            # on/off
  retry.count: 5           # AI suggestions to try (1-5)
  auto.update.code: true   # auto-patch source files
ai:
  provider: GEMINI         # GEMINI | OPENAI | CLAUDE
```

## Runtime overrides

```bash
mvn test -Dhealing.enabled=false           # Disable healing
mvn test -Dai.provider=OPENAI              # Switch provider
mvn test -Dhealing.retry.count=3           # Fewer retries
mvn test -Dbrowser.headless=true           # Headless mode
mvn test -Dtest=HealingDemoTest            # Run specific test
```

## Full documentation

- [USER_GUIDE.md](USER_GUIDE.md) -- Beginner-friendly walkthrough
- [USER_MANUAL.md](USER_MANUAL.md) -- Complete technical reference with 50 FAQ
