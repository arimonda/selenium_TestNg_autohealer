# AI Self-Healing Test Automation Framework

A professional-grade **Java Selenium + TestNG** framework that autonomously repairs broken selectors at runtime using AI (Gemini, OpenAI, or Claude).

---

## How It Works

```
Selector Breaks → Capture Page (HTML + Screenshot) → Ask AI for 5 Alternatives
→ Try Each One → First Working Selector Wins → Auto-Update Source Code
```

When `smartClick()` or `smartFill()` encounters a `NoSuchElementException` or `TimeoutException`, the framework:

1. Captures page HTML, screenshot, and computed CSS
2. Sends to your configured AI (Gemini/OpenAI/Claude) with a multimodal prompt
3. Receives 5 alternative CSS + XPath selectors
4. Validates each sequentially against the live DOM
5. Continues the test with the first working selector
6. Auto-patches the source file (comments old selector, adds new one)
7. Caches the result so future runs skip the AI call

---

## Quick Start

```bash
# 1. Install dependencies
mvn clean install -DskipTests

# 2. Set your API key in .env
echo "GEMINI_API_KEY=your_key_here" > .env

# 3. Run the tests (includes AI healing demo)
mvn test
```

---

## Features

| Feature | Description |
|---------|-------------|
| **3 AI Providers** | Gemini, OpenAI, Claude -- all with multimodal vision support |
| **Multi-Locator Healing** | AI returns 5 alternatives; framework validates each sequentially |
| **Auto Source Code Patching** | Comments old selector, adds new one with backup |
| **Persistent Cache** | `healing-cache.json` eliminates repeated API calls |
| **TestNG Listeners** | Auto failure interception, retry, and HTML report generation |
| **Healing Report** | `ai-healing-report.html` showing healed vs failed tests |
| **Screenshot Capture** | Full page screenshots at the moment of failure |
| **HTML Redaction** | Masks passwords, tokens, emails before sending to AI |
| **Simplified DOM** | Strips scripts, styles, SVGs, meta -- optimizes token usage |
| **Computed CSS Capture** | Includes CSS properties for better element identification |
| **Centralized Config** | `healing-config.yaml` with system property overrides |

---

## Project Structure

```
src/main/java/com/automation/
├── ai/           # AIHealingEngine + GeminiClient + OpenAIClient + ClaudeClient
├── base/         # BasePage (smart actions + healing) + SeleniumFactory
├── config/       # HealingConfig (YAML loader)
├── listeners/    # AiHealingListener + RetryAnalyzer + HealingReportListener
├── models/       # User, DiagnosticCapture, HealingResult
├── pages/        # LoginPage, HealingDemoLoginPage, CheckboxPage, DropdownPage
└── utils/        # HtmlCleaner, SelectorCodeUpdater, SmartElement, JsonUtils

src/test/
├── java/.../tests/   # HealingDemoTest, LoginTest, CheckboxTest, DropdownTest
└── resources/data/   # JSON test data (login/, forms/, healing/, environments/)
```

---

## Configuration

### AI Provider (`.env`)
```bash
GEMINI_API_KEY=your_key    # or OPENAI_API_KEY or CLAUDE_API_KEY
```

### Healing Settings (`healing-config.yaml`)
```yaml
healing:
  enabled: true
  retry.count: 5
  auto.update.code: true
ai:
  provider: GEMINI    # GEMINI | OPENAI | CLAUDE
```

### Runtime Overrides
```bash
mvn test -Dhealing.enabled=false -Dai.provider=CLAUDE -Dhealing.retry.count=3
```

---

## Documentation

| Document | Audience | Content |
|----------|----------|---------|
| [USER_GUIDE.md](USER_GUIDE.md) | Beginners | Step-by-step setup and usage |
| [USER_MANUAL.md](USER_MANUAL.md) | Developers | Full technical reference, 50 FAQ, architecture diagrams |
| [QUICKSTART.md](QUICKSTART.md) | Everyone | Minimal steps to run |

---

## Technologies

- Java 17 &bull; Selenium 4.40.0 &bull; TestNG 7.8.0 &bull; Jackson 2.15.2 &bull; Lombok 1.18.30 &bull; Log4j2 2.21.1 &bull; Dotenv 3.0.0
