# User Guide - AI Self-Healing Test Automation Framework

> A beginner-friendly guide to get up and running quickly. No deep Java expertise required.

---

## What This Framework Does

This is a **Selenium + TestNG** test automation framework with a built-in AI brain. When a UI selector breaks (the website changed, an ID was renamed, a button moved), instead of failing the test immediately, the framework:

1. Captures the page (HTML + screenshot)
2. Asks an AI (Gemini, OpenAI, or Claude) for replacement selectors
3. Tries each suggestion until one works
4. Auto-updates your source code so the fix sticks

All of this happens **in real time** during test execution.

---

## Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| Java | 17+ | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Chrome/Edge/Firefox | Any modern version | Installed on your machine |
| Internet | Required | For test sites + AI API calls |

---

## Step 1: Download Dependencies

Open a terminal in the project root (where `pom.xml` lives) and run:

```bash
mvn clean install -DskipTests
```

This downloads all libraries. First run may take 2-3 minutes.

---

## Step 2: Configure Your Browser

Edit `src/main/resources/config.properties`:

```properties
# Options: chrome, chromium, firefox, edge
browser.type=chrome

# true = no visible window (good for CI), false = see the browser
browser.headless=false
```

---

## Step 3: Set Up Your AI Provider

Edit the `.env` file in the project root. You only need ONE provider:

### Option A: Gemini (Recommended)
```
GEMINI_API_KEY=your_gemini_key_here
GEMINI_MODEL=gemini-2.0-flash
```

### Option B: OpenAI
```
OPENAI_API_KEY=sk-your-openai-key-here
OPENAI_MODEL=gpt-4o
```

### Option C: Anthropic Claude
```
CLAUDE_API_KEY=sk-ant-your-claude-key-here
CLAUDE_MODEL=claude-sonnet-4-20250514
```

> **No API key?** Tests still run normally -- AI healing is just skipped.

---

## Step 4: Run the Tests

```bash
mvn test
```

This runs the full test suite defined in `testng.xml`:
- **AI Healing Demo Tests** -- Tests with intentionally broken selectors (watch the AI fix them)
- **Login Tests** -- Standard login with valid/invalid credentials
- **Checkbox Tests** -- Toggle checkboxes on a demo site
- **Dropdown Tests** -- Select dropdown options

---

## Step 5: Check the Results

After the run, look at:

| Output | Location |
|--------|----------|
| Console | Printed directly in your terminal |
| TestNG Report | `target/surefire-reports/index.html` |
| **AI Healing Report** | `target/surefire-reports/ai-healing-report.html` |
| Screenshots | `logs/screenshots/` |
| Healing Audit Log | `logs/ai-healing-audit.log` |
| General Log | `logs/application.log` |

The **AI Healing Report** is the most interesting one. It shows:
- Which tests had broken selectors
- What the AI suggested
- Which suggestion worked (highlighted in green)
- How long each healing took

---

## Understanding the Test Data

Test data lives in organized folders under `src/test/resources/data/`:

```
data/
├── user.json                    # Legacy user data
├── login/
│   ├── valid_credentials.json   # Valid login: tomsmith
│   └── invalid_credentials.json # Invalid login: wronguser
├── forms/
│   └── contact_form.json        # Sample form data
├── healing/
│   └── broken_selectors.json    # Documents which selectors are broken vs correct
└── environments/
    └── staging.json             # Base URLs and page paths
```

To use test data in a test:

```java
User user = JsonUtils.getTestData("login/valid_credentials.json", User.class);
```

---

## Understanding AI Healing (Simple Explanation)

### What happens when a selector breaks:

```
Step 1: Test tries to click "button.btn-primary-login"
Step 2: Element not found! (NoSuchElementException)
Step 3: Framework captures the page HTML + screenshot
Step 4: Sends to Gemini: "This selector failed. Here's the page. Find the button."
Step 5: Gemini returns 5 suggestions:
         1. button[type='submit']
         2. #login-button
         3. .fa-sign-in
         4. xpath=//button[contains(@class,'radius')]
         5. xpath=//form[@id='login']/button
Step 6: Framework tries each one until it finds the real button
Step 7: Uses the working selector and continues the test
Step 8: Updates your source code file with the fix
```

### The Healing Report shows this visually:

| Status | Test | Original Selector | Healed Selector |
|--------|------|--------------------|-----------------|
| **PASSED WITH HEALING** | testLoginWithBrokenSelectors | `button.btn-primary-login` | `button[type='submit']` |

---

## Healing Configuration

All healing settings live in `src/main/resources/healing-config.yaml`:

```yaml
healing:
  enabled: true           # Turn healing on/off globally
  retry.count: 5          # How many AI suggestions to try (1-5)
  auto.update.code: true  # Auto-patch your source files

ai:
  provider: GEMINI        # GEMINI, OPENAI, or CLAUDE

screenshot:
  enabled: true           # Capture screenshots on failure

dom:
  simplified: true        # Strip noise from HTML before sending to AI
  capture.css: true       # Include computed CSS styles
```

You can also override any setting via command line:

```bash
mvn test -Dhealing.enabled=false           # Disable healing
mvn test -Dhealing.retry.count=3           # Try only 3 suggestions
mvn test -Dhealing.auto.update.code=false  # Don't modify source files
mvn test -Dai.provider=OPENAI              # Switch to OpenAI
```

---

## Where Logs Are Saved

| Log File | What It Contains |
|----------|-----------------|
| `logs/application.log` | General framework activity |
| `logs/ai-healing-audit.log` | Every healing attempt: what failed, what was suggested, what worked |
| `logs/screenshots/*.png` | Page screenshots at the moment of failure |
| `healing-cache.json` | Successful heals cached for instant reuse |

---

## Adding Your Own Test

### 1. Create test data (JSON)

```json
// src/test/resources/data/myfeature/search_data.json
{
  "query": "automation framework",
  "expectedResult": "AI Self-Healing"
}
```

### 2. Create a Page Object

```java
// src/main/java/com/automation/pages/SearchPage.java
public class SearchPage extends BasePage {

    @SeleniumSelector(value = "input[name='q']")
    private SmartElement searchBox;

    @SeleniumSelector(value = "button[type='submit']")
    private SmartElement searchButton;

    public SearchPage(WebDriver driver) {
        super(driver);
    }

    public void search(String query) {
        searchBox.fill(query);
        searchButton.click();
    }
}
```

### 3. Create a Test

```java
// src/test/java/com/automation/tests/SearchTest.java
public class SearchTest {
    private SeleniumFactory seleniumFactory;
    private SearchPage searchPage;

    @BeforeClass
    public void setUp() {
        seleniumFactory = SeleniumFactory.getInstance();
        searchPage = new SearchPage(seleniumFactory.getDriver());
    }

    @Test
    public void testSearch() {
        searchPage.navigateTo("https://example.com/search");
        searchPage.search("automation");
    }
}
```

### 4. Register in testng.xml

```xml
<test name="Search Tests">
    <classes>
        <class name="com.automation.tests.SearchTest"/>
    </classes>
</test>
```

---

## Common Problems & Quick Fixes

| Problem | Fix |
|---------|-----|
| `mvn` command not found | Install Maven, add `bin/` to PATH, open a new terminal |
| Browser doesn't start | Run `mvn test` again (first run downloads drivers via Selenium Manager) |
| Tests timeout | Check internet connection; try `browser.headless=true` |
| AI healing not working | Verify `.env` has a valid API key; check `logs/ai-healing-audit.log` |
| Wrong browser opens | Change `browser.type` in `config.properties` |
| Source code not updating | Check `healing.auto.update.code: true` in `healing-config.yaml` |
| Healing too slow | Reduce `retry.count` to 3; use Gemini Flash (fastest) |

---

## Project Structure at a Glance

```
Java_selenium_Playwright_Autohealing/
├── .env                          # API keys (git-ignored)
├── pom.xml                       # Maven dependencies
├── testng.xml                    # Test suite definition
├── healing-cache.json            # Auto-generated healing cache
│
├── src/main/java/com/automation/
│   ├── ai/                       # AI engine + LLM clients
│   │   ├── AIHealingEngine.java  #   Core healing logic
│   │   ├── LLMClient.java        #   Provider interface
│   │   ├── GeminiClient.java     #   Google Gemini
│   │   ├── OpenAIClient.java     #   OpenAI GPT
│   │   ├── ClaudeClient.java     #   Anthropic Claude
│   │   ├── HealingCache.java     #   Persistent cache
│   │   └── HtmlRedactor.java     #   Sensitive data redaction
│   ├── base/                     # Core framework
│   │   ├── BasePage.java         #   Smart actions + healing
│   │   └── SeleniumFactory.java  #   WebDriver lifecycle
│   ├── config/
│   │   └── HealingConfig.java    #   YAML config loader
│   ├── listeners/                # TestNG integration
│   │   ├── AiHealingListener.java      # Failure interception
│   │   ├── AiHealingRetryAnalyzer.java # Auto-retry on failure
│   │   ├── HealingReportListener.java  # HTML report generation
│   │   ├── HealingContext.java         # Shared healing state
│   │   └── RetryTransformer.java       # Auto-apply retry
│   ├── models/
│   │   ├── User.java
│   │   ├── DiagnosticCapture.java      # Failure snapshot
│   │   └── HealingResult.java          # Healing outcome
│   ├── pages/                    # Page Objects
│   │   ├── LoginPage.java
│   │   ├── HealingDemoLoginPage.java   # Broken selectors demo
│   │   ├── CheckboxPage.java
│   │   ├── DropdownPage.java
│   │   ├── SeleniumSelector.java
│   │   └── ElementInitializer.java
│   └── utils/
│       ├── HtmlCleaner.java
│       ├── JsonUtils.java
│       ├── SelectorCodeUpdater.java
│       └── SmartElement.java
│
├── src/main/resources/
│   ├── config.properties         # Browser settings
│   ├── healing-config.yaml       # Healing settings
│   └── log4j2.xml               # Logging config
│
├── src/test/java/com/automation/tests/
│   ├── HealingDemoTest.java      # Broken selector demo
│   ├── LoginTest.java            # Login tests
│   ├── CheckboxTest.java         # Checkbox tests
│   └── DropdownTest.java         # Dropdown tests
│
├── src/test/resources/data/      # Test data (input)
│   ├── user.json
│   ├── login/
│   │   ├── valid_credentials.json
│   │   └── invalid_credentials.json
│   ├── forms/
│   │   └── contact_form.json
│   ├── healing/
│   │   └── broken_selectors.json
│   └── environments/
│       └── staging.json
│
└── logs/                         # Output (git-ignored)
    ├── application.log
    ├── ai-healing-audit.log
    └── screenshots/
```

---

## Glossary

| Term | Meaning |
|------|---------|
| **Selector** | A CSS or XPath string that identifies an HTML element (e.g., `#username`, `button[type='submit']`) |
| **Healing** | The process of automatically finding a new selector when the original one breaks |
| **Page Object** | A Java class that represents a web page, with methods for interacting with it |
| **SmartElement** | A wrapper around a Selenium element that supports auto-healing |
| **BasePage** | The parent class all Page Objects extend -- provides smartClick, smartFill, and healing |
| **LLM** | Large Language Model -- the AI that analyzes HTML and suggests replacement selectors |
| **DiagnosticCapture** | A structured snapshot of everything at the moment of failure (HTML, screenshot, CSS, URL) |
| **HealingResult** | The outcome of a healing attempt (HEALED, FAILED, SKIPPED, CACHE_HIT) |
