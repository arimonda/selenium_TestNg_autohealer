# Architectural Document: Java Selenium AI Self-Healing Test Framework

> **Version:** 1.0.0  
> **Date:** February 16, 2026  
> **Artifact ID:** `selenium-TestNg-AutoHealing`  
> **Group ID:** `com.automation`  
> **Java Version:** 17  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Overview](#2-system-overview)
3. [Project Structure](#3-project-structure)
4. [Architecture Layers](#4-architecture-layers)
5. [Core Design Patterns](#5-core-design-patterns)
6. [Component Deep Dive](#6-component-deep-dive)
   - 6.1 [Base Layer](#61-base-layer---comautomationbase)
   - 6.2 [AI Healing Engine Layer](#62-ai-healing-engine-layer---comautomationai)
   - 6.3 [Configuration Layer](#63-configuration-layer---comautomationconfig)
   - 6.4 [Listener & TestNG Integration Layer](#64-listener--testng-integration-layer---comautomationlisteners)
   - 6.5 [Page Object Layer](#65-page-object-layer---comautomationpages)
   - 6.6 [Utility Layer](#66-utility-layer---comautomationutils)
   - 6.7 [Data Model Layer](#67-data-model-layer---comautomationmodels)
   - 6.8 [Test Layer](#68-test-layer---comautomationtests)
7. [Self-Healing Flow (End-to-End)](#7-self-healing-flow-end-to-end)
8. [Multi-Locator Healing Strategy](#8-multi-locator-healing-strategy)
9. [LLM Provider Architecture](#9-llm-provider-architecture)
10. [Prompt Engineering](#10-prompt-engineering)
11. [Caching Strategy](#11-caching-strategy)
12. [Source Code Auto-Patching](#12-source-code-auto-patching)
13. [HTML Processing Pipeline](#13-html-processing-pipeline)
14. [Screenshot & Multimodal Vision](#14-screenshot--multimodal-vision)
15. [Security Architecture](#15-security-architecture)
16. [Logging & Audit Trail](#16-logging--audit-trail)
17. [Reporting System](#17-reporting-system)
18. [Configuration Management](#18-configuration-management)
19. [Dependency Management](#19-dependency-management)
20. [Test Data Architecture](#20-test-data-architecture)
21. [WebDriver Lifecycle Management](#21-webdriver-lifecycle-management)
22. [Thread Safety Model](#22-thread-safety-model)
23. [Error Handling Strategy](#23-error-handling-strategy)
24. [Data Flow Diagrams](#24-data-flow-diagrams)
25. [Class Relationship Diagram](#25-class-relationship-diagram)
26. [Configuration Reference](#26-configuration-reference)
27. [Extension Points](#27-extension-points)
28. [Known Limitations & Trade-offs](#28-known-limitations--trade-offs)

---

## 1. Executive Summary

This project is a **professional-grade Selenium + TestNG test automation framework** enhanced with an **AI-powered self-healing engine**. When a CSS/XPath selector breaks at runtime (due to UI changes, refactoring, or dynamic IDs), the framework automatically:

1. **Detects** the locator failure (`NoSuchElementException`, `TimeoutException`, `StaleElementReferenceException`)
2. **Captures** full diagnostic context (page HTML, focused DOM snippet, screenshot, computed CSS styles)
3. **Consults** a Large Language Model (Gemini, OpenAI, or Claude) with multimodal vision support
4. **Receives** up to 5 replacement selector suggestions (mix of CSS and XPath)
5. **Validates** each suggestion sequentially against the live page
6. **Persists** the winning selector in-memory, in a JSON cache, and optionally auto-patches the Java source code
7. **Reports** all healing events in both a console summary and a styled HTML report

The net effect: **tests that would normally fail due to stale selectors now self-repair and continue executing**, dramatically reducing maintenance overhead.

---

## 2. System Overview

```
+-------------------------------------------------------------------+
|                        TEST EXECUTION                              |
|  +-------------------+   +------------------+   +--------------+  |
|  | HealingDemoTest   |   | LoginTest        |   | CheckboxTest |  |
|  | DropdownTest      |   |                  |   |              |  |
|  +--------+----------+   +--------+---------+   +------+-------+  |
|           |                       |                     |          |
|           v                       v                     v          |
|  +--------------------------------------------------------+       |
|  |              PAGE OBJECT LAYER (BasePage)               |       |
|  |  LoginPage | HealingDemoLoginPage | CheckboxPage | ... |       |
|  |  @SeleniumSelector -> SmartElement -> ElementInitializer|       |
|  +-------------------+------------------------------------+       |
|                      |                                             |
|        +-------------+---------------+                             |
|        |   smartClick / smartFill    |                              |
|        |   (First try original)      |                              |
|        |         |                   |                              |
|        |    FAIL? (NoSuchElement)    |                              |
|        |         |                   |                              |
|        v         v                   |                              |
|  +------------------------------------+                            |
|  |         AI HEALING ENGINE           |                            |
|  |  +----------+  +---------------+   |                            |
|  |  | HTML     |  | HTML          |   |                            |
|  |  | Cleaner  |  | Redactor      |   |                            |
|  |  +----------+  +---------------+   |                            |
|  |  +----------+  +---------------+   |                            |
|  |  | Prompt   |  | Healing       |   |                            |
|  |  | Builder  |  | Cache         |   |                            |
|  |  +----------+  +---------------+   |                            |
|  |  +------------------------------+ |                            |
|  |  |     LLM Client (Strategy)     | |                            |
|  |  |  Gemini | OpenAI | Claude     | |                            |
|  |  +------------------------------+ |                            |
|  +------------------------------------+                            |
|        |                                                           |
|        v                                                           |
|  +------------------------------------+                            |
|  |      PERSISTENCE & REPORTING        |                            |
|  |  SelectorCodeUpdater (auto-patch)   |                            |
|  |  HealingContext (result store)       |                            |
|  |  HealingReportListener (HTML/CLI)   |                            |
|  +------------------------------------+                            |
|                                                                    |
|  +------------------------------------+                            |
|  |      TESTNG LISTENER LAYER          |                            |
|  |  AiHealingListener (failure hook)   |                            |
|  |  AiHealingRetryAnalyzer (retry)     |                            |
|  |  RetryTransformer (auto-apply)      |                            |
|  +------------------------------------+                            |
+-------------------------------------------------------------------+
```

---

## 3. Project Structure

```
Java_selenium_Autohealing/
├── .env                                          # API keys (git-ignored)
├── .gitignore                                    # Exclusion rules
├── pom.xml                                       # Maven build descriptor
├── testng.xml                                    # TestNG suite definition
├── README.md                                     # Main documentation
├── QUICKSTART.md                                 # Quick start guide
├── USER_GUIDE.md                                 # Beginner-friendly guide
├── USER_MANUAL.md                                # Complete technical reference (50 FAQ)
├── learning.md                                   # Learning notes
│
├── src/main/java/com/automation/
│   ├── ai/                                       # AI Healing Engine Layer
│   │   ├── AIHealingEngine.java                  #   Core orchestrator
│   │   ├── LLMClient.java                        #   Strategy interface
│   │   ├── GeminiClient.java                     #   Google Gemini implementation
│   │   ├── OpenAIClient.java                     #   OpenAI GPT implementation
│   │   ├── ClaudeClient.java                     #   Anthropic Claude implementation
│   │   ├── HealingCache.java                     #   Persistent JSON cache
│   │   └── HtmlRedactor.java                     #   Sensitive data redaction
│   │
│   ├── base/                                     # Core Framework Layer
│   │   ├── BasePage.java                         #   Abstract base with healing logic
│   │   └── SeleniumFactory.java                  #   WebDriver singleton factory
│   │
│   ├── config/                                   # Configuration Layer
│   │   └── HealingConfig.java                    #   Centralized YAML/property config
│   │
│   ├── listeners/                                # TestNG Integration Layer
│   │   ├── AiHealingListener.java                #   Failure interception listener
│   │   ├── AiHealingRetryAnalyzer.java           #   Automatic retry on locator failure
│   │   ├── RetryTransformer.java                 #   Auto-applies retry analyzer
│   │   ├── HealingReportListener.java            #   HTML + console report generator
│   │   └── HealingContext.java                   #   Thread-safe result store
│   │
│   ├── models/                                   # Data Model Layer
│   │   ├── User.java                             #   Test data POJO
│   │   ├── DiagnosticCapture.java                #   Failure snapshot model
│   │   └── HealingResult.java                    #   Healing outcome model
│   │
│   ├── pages/                                    # Page Object Layer
│   │   ├── LoginPage.java                        #   Login page (correct selectors)
│   │   ├── HealingDemoLoginPage.java             #   Login page (broken selectors)
│   │   ├── CheckboxPage.java                     #   Checkbox page
│   │   ├── DropdownPage.java                     #   Dropdown page
│   │   ├── SeleniumSelector.java                 #   Custom annotation
│   │   └── ElementInitializer.java               #   Reflection-based field initializer
│   │
│   └── utils/                                    # Utility Layer
│       ├── HtmlCleaner.java                      #   HTML noise removal
│       ├── JsonUtils.java                        #   JSON test data loader
│       ├── SelectorCodeUpdater.java              #   Source code auto-patcher
│       └── SmartElement.java                     #   Self-healing element wrapper
│
├── src/main/resources/
│   ├── config.properties                         # Browser configuration
│   ├── healing-config.yaml                       # AI healing configuration
│   └── log4j2.xml                                # Logging configuration
│
├── src/test/java/com/automation/tests/
│   ├── HealingDemoTest.java                      # AI healing demonstration tests
│   ├── LoginTest.java                            # Functional login tests
│   ├── CheckboxTest.java                         # Checkbox interaction tests
│   └── DropdownTest.java                         # Dropdown interaction tests
│
└── src/test/resources/data/
    ├── user.json                                 # User credentials
    ├── login/
    │   ├── valid_credentials.json                # Valid login data
    │   └── invalid_credentials.json              # Invalid login data
    ├── forms/
    │   └── contact_form.json                     # Contact form data
    ├── healing/
    │   └── broken_selectors.json                 # Broken selector documentation
    └── environments/
        └── staging.json                          # Environment-specific config
```

**Statistics:**
| Metric | Count |
|---|---|
| Java source files (main) | 20 |
| Java source files (test) | 4 |
| Configuration files | 5 |
| Test data files (JSON) | 6 |
| Documentation files | 5 |
| Total packages | 8 |

---

## 4. Architecture Layers

The framework follows a strict **layered architecture** with clear separation of concerns:

```
┌──────────────────────────────────────────────┐
│  LAYER 7: TEST LAYER                         │  Tests (TestNG)
├──────────────────────────────────────────────┤
│  LAYER 6: PAGE OBJECT LAYER                  │  Page Objects + SmartElement
├──────────────────────────────────────────────┤
│  LAYER 5: BASE LAYER (HEALING ORCHESTRATOR)  │  BasePage (smartClick/smartFill)
├──────────────────────────────────────────────┤
│  LAYER 4: AI ENGINE LAYER                    │  AIHealingEngine + LLM Clients
├──────────────────────────────────────────────┤
│  LAYER 3: LISTENER / OBSERVER LAYER          │  TestNG Listeners
├──────────────────────────────────────────────┤
│  LAYER 2: UTILITY LAYER                      │  HTML processing, caching, code updater
├──────────────────────────────────────────────┤
│  LAYER 1: CONFIGURATION & DATA LAYER         │  Config, models, test data
├──────────────────────────────────────────────┤
│  LAYER 0: INFRASTRUCTURE                     │  Selenium WebDriver, Maven, Log4j2
└──────────────────────────────────────────────┘
```

**Dependency Direction:** Each layer depends only on the layers below it. Tests -> Page Objects -> BasePage -> AI Engine -> Utilities -> Config. No upward dependencies exist.

---

## 5. Core Design Patterns

| Pattern | Where Used | Purpose |
|---|---|---|
| **Page Object Model (POM)** | `LoginPage`, `HealingDemoLoginPage`, `CheckboxPage`, `DropdownPage` | Encapsulates page interactions behind a clean API |
| **Strategy** | `LLMClient` interface + `GeminiClient`, `OpenAIClient`, `ClaudeClient` | Pluggable AI providers, swappable at runtime |
| **Singleton** | `SeleniumFactory`, `HealingConfig` | Single WebDriver instance, single config instance |
| **Observer / Listener** | `AiHealingListener`, `HealingReportListener`, `RetryTransformer` | Decouple test execution from healing/reporting |
| **Builder** | `HealingResult.builder()`, `DiagnosticCapture.builder()` | Fluent construction of complex immutable objects |
| **Template Method** | `BasePage` (abstract base for all page objects) | Shared healing logic, subclasses implement specifics |
| **Proxy / Wrapper** | `SmartElement` (wraps `WebElement` / `By`) | Intercepts element interactions to enable healing |
| **Factory Method** | `AIHealingEngine.defaultClient()`, `defaultCache()` | Decouple object creation from usage |
| **Chain of Responsibility** | Multi-locator healing (try suggestion 1, then 2, then 3...) | Sequential fallback through AI suggestions |
| **Annotation Processing** | `@SeleniumSelector` + `ElementInitializer` | Declarative element binding via reflection |

---

## 6. Component Deep Dive

### 6.1 Base Layer - `com.automation.base`

#### `SeleniumFactory.java` — WebDriver Singleton Factory

**Role:** Manages the entire lifecycle of the Selenium WebDriver instance.

**Pattern:** Thread-safe Singleton with lazy initialization.

**Key Responsibilities:**
- Loads browser configuration from `config.properties`
- Creates the appropriate WebDriver (Chrome, Firefox, or Edge)
- Configures headless mode, window size, GPU settings, sandbox settings
- Sets implicit wait to `Duration.ZERO` (explicit waits are used throughout)
- Maximizes the browser window on creation
- Provides `close()` to quit the driver and reset the singleton

**Browser Support:**
| Browser | Driver | Headless Support |
|---|---|---|
| Chrome/Chromium | `ChromeDriver` | `--headless=new` |
| Firefox | `FirefoxDriver` | `-headless` |
| Edge | `EdgeDriver` | `--headless=new` |

**Chrome-specific Options:**
- `--disable-gpu` — Prevents GPU rendering issues
- `--window-size=1920,1080` — Consistent viewport for screenshots
- `--no-sandbox` — Required for CI/container environments
- `--disable-dev-shm-usage` — Prevents shared memory issues in Docker

**Lifecycle:**
```
getInstance() -> loadConfig() -> initializeDriver() -> getDriver()
                                                       ...
close() -> driver.quit() -> instance = null
```

---

#### `BasePage.java` — Abstract Base with Self-Healing

**Role:** The central orchestrator of the self-healing mechanism. Every page object extends this class.

**Key API:**
| Method | Description |
|---|---|
| `smartClick(String selector)` | Click with healing for inline selectors |
| `smartClick(SmartElement element)` | Click with healing for annotated elements |
| `smartFill(String selector, String value)` | Fill with healing for inline selectors |
| `smartFill(SmartElement element, String value)` | Fill with healing for annotated elements |
| `navigateTo(String url)` | Navigate to a URL |
| `waitForVisible(By by)` | Explicit wait for visibility (10s) |
| `waitForClickable(By by)` | Explicit wait for clickability (10s) |

**Healing Flow (per `smartClick`/`smartFill`):**

```
1. TRY original selector
   ├── SUCCESS → return immediately
   └── FAIL (NoSuchElement / Timeout / StaleElement)
       │
       ├── Is healing enabled? NO → re-throw exception
       │
       └── YES → ENTER HEAL MODE
           │
           ├── Build focused HTML snippet (buildFocusHtml)
           ├── Capture screenshot (captureScreenshotBase64AndSave)
           ├── Call healingEngine.healMultiple(...)
           │     → Returns List<String> of up to 5 suggestions
           │
           ├── FOR EACH suggestion:
           │   ├── Convert to By (CSS or XPath)
           │   ├── Wait up to 3 seconds for element
           │   ├── Attempt action (click or fill)
           │   ├── SUCCESS → record as winner, break
           │   └── FAIL → try next suggestion
           │
           ├── Record HealingResult in HealingContext
           │
           ├── If winner found:
           │   ├── Update SmartElement in-memory
           │   └── Auto-patch source code (if configured)
           │
           └── If no winner:
               └── Throw NoSuchElementException
```

**Wait Configuration:**
- `DEFAULT_WAIT` = 10 seconds — For initial selector attempts
- `HEAL_VALIDATION_WAIT` = 3 seconds — For validating each AI suggestion

**Focus HTML Builder (`buildFocusHtml`):**

This method creates a targeted HTML snippet to improve AI accuracy by narrowing the context:

1. **Direct match attempt** — Try to find elements matching the failed selector, extract their `outerHTML` including 2 parent levels
2. **Heuristic fallback** — If direct match fails, derive candidate selectors (extract ID patterns, attribute patterns from the selector) and search for nearby elements
3. **Computed CSS capture** — If enabled, captures computed CSS styles (`display`, `visibility`, `opacity`, `position`, `width`, `height`, etc.) via JavaScript

The focus HTML is capped at **20,000 characters** and sent alongside the full page HTML to the AI.

**Selector Conversion (`toBy`):**

| Input Format | Conversion |
|---|---|
| `xpath=//div[@id='x']` | `By.xpath("//div[@id='x']")` |
| `//div[@id='x']` | `By.xpath("//div[@id='x']")` |
| `(//div[@id='x'])` | `By.xpath("(//div[@id='x'])")` |
| `#username` | `By.cssSelector("#username")` |
| `input[name='user']` | `By.cssSelector("input[name='user']")` |

---

### 6.2 AI Healing Engine Layer - `com.automation.ai`

#### `AIHealingEngine.java` — Core Healing Orchestrator

**Role:** The brain of the self-healing system. Coordinates cache lookups, HTML processing, prompt building, LLM communication, and response parsing.

**Constructor Overloads:**
```java
AIHealingEngine()                               // Default: auto-detect provider + default cache
AIHealingEngine(LLMClient)                       // Custom provider + default cache
AIHealingEngine(LLMClient, HealingCache)         // Full customization
```

**Primary Method: `healMultiple()`**

```
healMultiple(failedSelector, htmlContent, allowCache, focusHtml, screenshotBase64Png)
    │
    ├── Guard: null/empty selector → return empty list
    ├── Guard: healing disabled → return empty list
    │
    ├── 1. CACHE CHECK
    │   └── If cached → return List.of(cachedSelector)
    │
    ├── 2. HTML PROCESSING
    │   ├── HtmlCleaner.cleanHtml(htmlContent)
    │   └── HtmlRedactor.redact(cleanedHtml)
    │
    ├── 3. PROMPT CONSTRUCTION
    │   └── buildPrompt(failedSelector, redactedHtml, focusHtml, screenshot)
    │
    ├── 4. LLM CALL
    │   ├── Text-only: llmClient.sendPrompt(prompt)
    │   └── Multimodal: llmClient.sendPrompt(prompt, screenshotBase64)
    │
    ├── 5. RESPONSE PARSING
    │   └── parseMultipleSelectors(rawResponse)
    │
    ├── 6. CACHING (first suggestion)
    │   └── healingCache.put(failedSelector, firstSuggestion)
    │
    └── 7. RETURN List<String> suggestions
```

**Response Parser:**

The `parseMultipleSelectors` method handles multiple LLM response formats:
- Numbered lists: `1. #username`, `2) input[name='user']`, `3: xpath=//input[@id]`
- Plain lines (one selector per line)
- Strips surrounding quotes (`"..."` or `'...'`)
- Validates each candidate with `looksLikeSelector()`:
  - Accepts `xpath=` prefixed selectors
  - Accepts `//` or `(//` XPath selectors
  - Accepts CSS selectors containing `#`, `.`, `[`, `:`, `>`, `~`, `+`, or word characters
- Caps results at `retryCount` (default 5)

**Provider Factory (`defaultClient`):**

```
1. Check ai.provider in HealingConfig
   ├── "CLAUDE"  → new ClaudeClient()
   ├── "OPENAI"  → new OpenAIClient()
   ├── "GEMINI"  → new GeminiClient()
   │
   └── Default fallback chain:
       ├── GEMINI_API_KEY set?  → GeminiClient
       ├── CLAUDE_API_KEY set?  → ClaudeClient
       ├── OPENAI_API_KEY set?  → OpenAIClient
       └── Default              → GeminiClient (will warn)
```

---

#### `LLMClient.java` — Strategy Interface

```java
public interface LLMClient {
    String sendPrompt(String prompt);
    default String sendPrompt(String prompt, String screenshotBase64Png) {
        return sendPrompt(prompt);  // Fallback to text-only
    }
}
```

This interface enables the **Strategy Pattern** — any AI provider can be plugged in by implementing this two-method interface. The `default` method on the multimodal overload ensures backward compatibility for text-only providers.

---

#### `GeminiClient.java` — Google Gemini Integration

**API:** Google Generative Language API v1beta  
**Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=...`  
**Default Model:** `gemini-2.0-flash`

**Configuration Resolution (highest precedence first):**
| Setting | System Property | .env Variable | Env Variable | Default |
|---|---|---|---|---|
| API Key | — | `GEMINI_API_KEY` | `GEMINI_API_KEY` | — |
| Model | `gemini.model` | `GEMINI_MODEL` | `GEMINI_MODEL` | `gemini-2.0-flash` |
| System Prompt | `gemini.systemPrompt` | `GEMINI_SYSTEM_PROMPT` | `GEMINI_SYSTEM_PROMPT` | Built-in |
| Max Image Chars | `gemini.image.maxBase64Chars` | — | `GEMINI_IMAGE_MAX_BASE64_CHARS` | 2,000,000 |

**Multimodal Support:**
- Text-only: Single `text` part in the `contents` array
- Vision: `text` part + `inline_data` part with `mime_type: image/png` and base64-encoded data
- Screenshot size guard: If base64 exceeds `maxImageBase64Chars`, falls back to text-only

**Request Structure (Multimodal):**
```json
{
  "systemInstruction": { "parts": [{ "text": "<system prompt>" }] },
  "contents": [{
    "role": "user",
    "parts": [
      { "text": "<prompt>" },
      { "inline_data": { "mime_type": "image/png", "data": "<base64>" } }
    ]
  }]
}
```

**HTTP Configuration:**
- Connect timeout: 30 seconds
- Request timeout: 90 seconds

---

#### `OpenAIClient.java` — OpenAI GPT Integration

**API:** OpenAI Chat Completions API  
**Endpoint:** `POST https://api.openai.com/v1/chat/completions`  
**Default Model:** `gpt-5.2`  
**Auth:** `Authorization: Bearer <API_KEY>`

**Configuration Resolution:** Same pattern as Gemini (system property > .env > env var > default).

**Request Parameters:**
- `temperature: 0.1` — Low temperature for deterministic, precise selector output
- `max_tokens: 300` — Sufficient for 5 selector lines

**Multimodal Support:**
- Uses OpenAI's Vision API format with `image_url` content blocks
- Image embedded as `data:image/png;base64,...` data URI

---

#### `ClaudeClient.java` — Anthropic Claude Integration

**API:** Anthropic Messages API  
**Endpoint:** `POST https://api.anthropic.com/v1/messages`  
**Default Model:** `claude-sonnet-4-20250514`  
**Auth Headers:**
- `x-api-key: <API_KEY>`
- `anthropic-version: 2023-06-01`

**Multimodal Support:**
- Uses Claude's native `image` content block format
- Image sent as `{ "type": "image", "source": { "type": "base64", "media_type": "image/png", "data": "..." } }`

**Request Parameters:**
- `max_tokens: 300`
- `system: <system prompt>` (top-level field, not a message)

---

#### `HealingCache.java` — Persistent JSON Cache

**Role:** Avoids redundant LLM API calls by caching `failedSelector -> healedSelector` mappings.

**Storage:** `healing-cache.json` in the project working directory (configurable via `healing.cache.path` system property).

**Operations:**
| Method | Description |
|---|---|
| `get(failedSelector)` | Returns `Optional<String>` of cached healed selector |
| `put(failedSelector, healedSelector)` | Caches and immediately persists |
| `snapshot()` | Returns unmodifiable copy of entire cache |

**Persistence Strategy:**
1. Serialize to `.tmp` file using Jackson's pretty printer
2. Atomic move from `.tmp` to actual cache path (`ATOMIC_MOVE`)
3. If atomic move fails (Windows/older FS), retry with simple `REPLACE_EXISTING`

**Thread Safety:** All public methods are `synchronized`.

**Lazy Loading:** Cache is loaded from disk on first access (`ensureLoaded()`), not at construction time.

---

#### `HtmlRedactor.java` — Sensitive Data Redaction

**Role:** Removes potentially sensitive information from HTML before sending to external LLM APIs.

**Redaction Rules (applied in order):**
| Pattern | What It Catches | Replacement |
|---|---|---|
| Password input values | `<input type="password" value="secret">` | `[REDACTED_PASSWORD]` |
| JWT-like tokens | `eyJhbG...` (three base64url segments separated by dots) | `[REDACTED_TOKEN]` |
| Email addresses | `user@example.com` | `[REDACTED_EMAIL]@example.com` (domain preserved) |
| Generic value attributes | `value="anything"` | `value="[REDACTED]"` |

**Design:** Final utility class with private constructor — purely static methods, no instantiation.

---

### 6.3 Configuration Layer - `com.automation.config`

#### `HealingConfig.java` — Centralized Configuration

**Role:** Single source of truth for all framework configuration.

**Pattern:** Double-checked locking Singleton with volatile instance.

**Configuration Precedence (highest wins):**
```
System Properties (-Dhealing.enabled=false)
    ↓ overrides
healing-config.yaml (classpath)
    ↓ overrides
Hard-coded defaults
```

**Configuration Properties:**

| Property | Type | Default | System Property Override |
|---|---|---|---|
| `healing.enabled` | boolean | `true` | `-Dhealing.enabled` |
| `healing.retry.count` | int | `5` | `-Dhealing.retry.count` |
| `healing.auto.update.code` | boolean | `true` | `-Dhealing.auto.update.code` |
| `healing.max.html.chars` | int | `200,000` | `-Dhealing.max.html.chars` |
| `ai.provider` | String | `GEMINI` | `-Dai.provider` |
| `ai.gemini.model` | String | `gemini-2.0-flash` | via YAML only |
| `ai.openai.model` | String | `gpt-4o` | via YAML only |
| `ai.claude.model` | String | `claude-sonnet-4-20250514` | via YAML only |
| `screenshot.enabled` | boolean | `true` | `-Dhealing.screenshot.enabled` |
| `screenshot.directory` | String | `logs/screenshots` | via YAML only |
| `dom.simplified` | boolean | `true` | `-Dhealing.dom.simplified` |
| `dom.capture.css` | boolean | `true` | `-Dhealing.dom.capture.css` |

**Custom YAML Parser:**
The framework includes a **minimal, zero-dependency YAML parser** (`parseSimpleYaml`) that supports:
- Top-level sections (indent 0)
- 2-level nesting (indent 2-4 spaces)
- Inline comment stripping (` #...`)
- Boolean, Integer, Long, and String value parsing

This avoids a dependency on SnakeYAML or Jackson YAML.

**Reload Support:** `HealingConfig.reload()` resets the singleton and re-reads configuration. Useful for test isolation.

---

### 6.4 Listener & TestNG Integration Layer - `com.automation.listeners`

#### `AiHealingListener.java` — Test Failure Interceptor

**Role:** Acts as a **diagnostic safety net**. While `BasePage.smartClick/smartFill` handle healing inline during execution, this listener captures diagnostics for failures that bypass the Page Object layer.

**Implements:** `ITestListener`

**Lifecycle Hooks:**

| Hook | Action |
|---|---|
| `onTestStart` | Log test start |
| `onTestSuccess` | Log test success |
| `onTestFailure` | **Core logic**: Intercept, diagnose, heal |
| `onTestSkipped` | Log skip |
| `onStart` | Log suite start, reset audit log |
| `onFinish` | Log suite end with aggregate stats |

**Failure Detection (`isLocatorFailure`):**
The listener checks the entire exception chain for locator-related failures:
- `NoSuchElementException`
- `TimeoutException`
- `StaleElementReferenceException`
- `InvalidSelectorException`
- Message pattern matching: "no such element", "unable to locate", "element not found", "timeout", "stale element"
- **Recursive cause-chain traversal** for wrapped exceptions

**Selector Extraction (`extractFailedSelector`):**
Parses the exception message to extract the failing selector string:
- `"selector":"..."` pattern (from `NoSuchElementException`)
- `By.cssSelector: ...` pattern (from `TimeoutException`)
- `By.xpath: ...` pattern (from XPath-based failures)
- Falls back to `"unknown"` if parsing fails

**WebDriver Extraction (`extractDriver`):**
Uses **reflection** to obtain the WebDriver from the test instance:
1. Scans all declared fields of the test class
2. If a field is a `WebDriver`, returns it directly
3. If a field is a `SeleniumFactory`, calls `getDriver()` reflectively
4. Returns `null` if driver cannot be found (healing is skipped)

**Diagnostic Capture Pipeline:**
```
onTestFailure
    ├── isLocatorFailure? NO → skip
    ├── extractDriver → null? → recordSkipped
    ├── extractFailedSelector
    ├── buildDiagnosticCapture
    │   ├── driver.getPageSource()
    │   ├── HtmlCleaner.cleanHtml(pageSource)
    │   ├── captureScreenshot (save to disk + base64)
    │   ├── driver.getCurrentUrl()
    │   └── driver.getTitle()
    ├── healingEngine.healMultiple(...)
    ├── Validate suggestions against live page
    ├── Build HealingResult
    └── HealingContext.record(result)
```

---

#### `AiHealingRetryAnalyzer.java` — Automatic Retry

**Implements:** `IRetryAnalyzer`

**Behavior:** Allows **exactly one automatic retry** for tests that fail due to locator issues. The first execution triggers healing (which populates the cache), and the retry benefits from the cached healed selector.

**Maximum Retries:** 1 (hardcoded for safety to prevent infinite loops)

---

#### `RetryTransformer.java` — Automatic Retry Application

**Implements:** `IAnnotationTransformer`

**Role:** Automatically applies `AiHealingRetryAnalyzer` to **every test method** in the suite, eliminating the need for manual `@Test(retryAnalyzer = ...)` annotations on each test.

**Mechanism:** Intercepts TestNG's annotation processing phase and sets the retry analyzer on any test annotation that doesn't already have one.

---

#### `HealingContext.java` — Thread-Safe Result Store

**Role:** Central repository for all `HealingResult` objects across the entire test run. Used by the listener to collect data and by the reporter to generate the report.

**Thread Safety:** Uses `CopyOnWriteArrayList` — optimized for read-heavy, write-occasional patterns.

**API:**
| Method | Description |
|---|---|
| `record(HealingResult)` | Add a result |
| `getResults()` | Unmodifiable snapshot |
| `healedCount()` | Count of HEALED + CACHE_HIT outcomes |
| `failedCount()` | Count of FAILED outcomes |
| `clear()` | Reset (for test isolation) |

---

#### `HealingReportListener.java` — Report Generator

**Implements:** `IReporter`

**Generates:**
1. **Console Banner** — ASCII-art summary with total events, healed, failed, skipped counts
2. **HTML Report** — `target/surefire-reports/ai-healing-report.html`

**HTML Report Features:**
- Summary cards with color-coded metrics (Total: purple, Healed: green, Failed: red, Skipped: amber)
- Detailed results table with columns: Status, Test Class, Test Method, Original Selector, Healed Selector, AI Provider, Duration (ms), Suggestions
- Winning suggestion highlighted in bold with green background
- All AI suggestions listed as an ordered list
- Responsive design with modern CSS (Segoe UI, rounded cards, hover effects)
- Status badges: `PASSED WITH HEALING`, `CACHE HIT`, `FAILED`, `SKIPPED`

---

### 6.5 Page Object Layer - `com.automation.pages`

#### `@SeleniumSelector` — Custom Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SeleniumSelector {
    String value();  // CSS or XPath selector string
}
```

**Purpose:** Declaratively binds Page Object fields to DOM selectors. Fields annotated with `@SeleniumSelector` are automatically initialized by `ElementInitializer` during page object construction.

---

#### `ElementInitializer.java` — Reflection-Based Field Initializer

**Role:** Scans a Page Object's declared fields for `@SeleniumSelector` annotations and creates `SmartElement` instances for each one.

**Process:**
1. Get the page object's class
2. Iterate all declared fields
3. For each `@SeleniumSelector` field:
   - Read the `value()` attribute
   - Convert to `By` (CSS or XPath)
   - Create a `SmartElement(driver, by, selector, basePage, ownerClass, fieldName)`
   - Set the field value via `field.setAccessible(true)` + `field.set()`

**Key Detail:** The `SmartElement` receives the `ownerPageClass` and `fieldName` — these are required later for source code auto-patching.

---

#### Page Object Implementations

**`LoginPage.java`** — Correct selectors for `https://the-internet.herokuapp.com/login`:
- `input[name='username']`
- `input[name='password']`
- `button[type='submit']`

**`HealingDemoLoginPage.java`** — Intentionally **broken** selectors:
- `input[data-qa='user-login-field']` — No such attribute exists
- `#pass-field-old` — No such ID exists
- `button.btn-primary-login` — No such class exists

The demo page is the primary vehicle for demonstrating and testing the AI healing capability.

**`CheckboxPage.java`** — Wraps `#checkboxes` with toggle/check operations.

**`DropdownPage.java`** — Wraps `#dropdown` with Selenium's `Select` class.

---

### 6.6 Utility Layer - `com.automation.utils`

#### `SmartElement.java` — Self-Healing Element Wrapper

**Role:** Wraps a Selenium `By` locator with metadata needed for healing (selector string, owner class, field name) and routes interactions through `BasePage`'s smart methods.

**Constructor Variants:**
| Constructor | Use Case |
|---|---|
| `(driver, by, selector)` | Standalone element, no healing |
| `(driver, by, selector, basePage)` | Healing-enabled, no source patching |
| `(driver, by, selector, basePage, ownerClass, fieldName)` | Full healing + source code auto-patching |

**Interaction Routing:**
- If `basePage` is set: `click()` → `basePage.smartClick(this)` (healing-enabled)
- If `basePage` is null: `click()` → `getElement().click()` (standard Selenium)

**Mutable State:** `updateSelector(newSelector)` allows the healing engine to update the selector in-memory after a successful heal, so subsequent interactions use the healed selector.

---

#### `SelectorCodeUpdater.java` — Source Code Auto-Patcher

**Role:** Automatically modifies Page Object Java source files when a selector is healed, ensuring the fix persists across runs.

**Behavior:**
1. Resolve the source file path: `com.automation.pages.LoginPage` → `src/main/java/com/automation/pages/LoginPage.java`
2. Read the file content
3. Find the `@SeleniumSelector(value = "oldSelector")` annotation above the `private SmartElement fieldName;` declaration
4. **Comment out** the old annotation (does not delete it)
5. **Insert** a new `@SeleniumSelector(value = "newSelector")` line below the commented-out line
6. Create a **timestamped backup** in `.selector_backups/` before writing

**Example transformation:**
```java
// Before:
@SeleniumSelector(value = "input[data-qa='user-login-field']")
private SmartElement usernameField;

// After:
// @SeleniumSelector(value = "input[data-qa='user-login-field']")
@SeleniumSelector(value = "#username")
private SmartElement usernameField;
```

**Safety Measures:**
- Only patches if the current selector in the file matches the expected `oldSelector`
- Creates a backup before every modification
- Returns `false` and logs a warning if the file cannot be found or modified
- Uses regex with `Pattern.quote(fieldName)` to prevent injection

---

#### `HtmlCleaner.java` — HTML Noise Removal

**Role:** Reduces raw page HTML to a clean version suitable for AI consumption, minimizing token waste.

**Two Modes:**

**Standard Mode (`cleanHtml`)** — Removes:
| Element/Pattern | Regex |
|---|---|
| `<script>...</script>` | `(?is)<script\b[^>]*>.*?</script>` |
| `<style>...</style>` | `(?is)<style\b[^>]*>.*?</style>` |
| `<svg>...</svg>` | `(?is)<svg\b[^>]*>.*?</svg>` |
| `<noscript>...</noscript>` | `(?is)<noscript\b[^>]*>.*?</noscript>` |
| `<meta ...>` | `(?i)<meta\b[^>]*/?>` |
| `<link ...>` | `(?i)<link\b[^>]*/?>` |
| HTML comments | `<!--[\s\S]*?-->` |
| Inline event handlers | `\s+on[a-z]+=\s*(['"]).*?\1` |
| Random data attributes | `\s+data-[a-z0-9-]+=\s*['"][a-f0-9]{8,}['"]` |
| Excessive whitespace | `\s{2,}` → single space |

**Skeleton Mode (`toSkeletonDom`)** — Aggressive mode that additionally strips ALL attributes except a whitelist:
- Preserved: `id`, `class`, `name`, `type`, `role`, `aria-*`, `data-testid`, `data-test`, `href`, `for`, `placeholder`, `title`, `value`, `action`, `method`

---

#### `JsonUtils.java` — Test Data Loader

**Role:** Loads JSON test data files and deserializes them to POJOs using Jackson.

**Resolution Order:**
1. Check file system at the literal path
2. Fall back to classpath resource

---

### 6.7 Data Model Layer - `com.automation.models`

#### `HealingResult.java`

| Field | Type | Description |
|---|---|---|
| `testName` | String | Test method name |
| `testClass` | String | Test class name |
| `originalSelector` | String | The selector that failed |
| `healedSelector` | String | The selector that worked (null if all failed) |
| `suggestedSelectors` | List\<String\> | All AI suggestions |
| `winningIndex` | int | 1-based index of winning suggestion (-1 if none) |
| `outcome` | HealingOutcome | HEALED, FAILED, SKIPPED, CACHE_HIT |
| `aiProvider` | String | GEMINI, OPENAI, or CLAUDE |
| `healingDurationMs` | long | Time spent on healing in milliseconds |
| `timestamp` | LocalDateTime | When healing was attempted |
| `codeUpdated` | boolean | Whether source code was auto-patched |
| `diagnosticCapture` | DiagnosticCapture | Full diagnostic snapshot |

---

#### `DiagnosticCapture.java`

Captures a complete snapshot of the failure context for AI analysis:

| Field | Type | Description |
|---|---|---|
| `timestamp` | LocalDateTime | When the failure occurred |
| `testMethodName` | String | Fully qualified test method |
| `failedSelector` | String | The selector that failed |
| `exceptionType` | String | e.g., "NoSuchElementException" |
| `exceptionMessage` | String | Full exception message |
| `fullPageHtml` | String | Complete page source |
| `simplifiedDom` | String | Cleaned HTML (noise removed) |
| `focusHtml` | String | Targeted snippet near the element |
| `computedCssStyles` | String | CSS computed styles JSON |
| `screenshotBase64Png` | String | Base64-encoded screenshot |
| `screenshotFilePath` | String | Path to saved screenshot file |
| `suggestedSelectors` | List\<String\> | AI suggestions |
| `healedSelector` | String | Winning selector |
| `healed` | boolean | Whether healing succeeded |
| `pageUrl` | String | Current page URL |
| `pageTitle` | String | Current page title |

---

#### `User.java`

Simple POJO for test data with Jackson `@JsonProperty` annotations:
- `username`, `password`, `email`

---

### 6.8 Test Layer - `com.automation.tests`

#### `HealingDemoTest.java` — AI Healing Demonstration

**Purpose:** Proves the self-healing system works with intentionally broken selectors.

**Test Cases:**
| Test | Description |
|---|---|
| `testLoginWithBrokenSelectors` | Uses `HealingDemoLoginPage` with 3 broken `@SeleniumSelector` annotations. AI heals each one inline. |
| `testSmartActionsWithBrokenSelectors` | Uses inline broken selectors via `smartFill`/`smartClick` (not from annotations). Tests healing for non-annotated selectors. |

**Target Site:** `https://the-internet.herokuapp.com/login`

**Expected Behavior:**
1. `enterUsername("tomsmith")` — Selector `input[data-qa='user-login-field']` fails → AI heals to `#username`
2. `enterPassword("SuperSecretPassword!")` — Selector `#pass-field-old` fails → AI heals to `#password`
3. `clickLogin()` — Selector `button.btn-primary-login` fails → AI heals to `button[type='submit']`
4. Assert landing on secure area page

---

#### `LoginTest.java` — Functional Tests

Uses `LoginPage` with **correct** selectors and JSON test data:
- `testValidLogin` — Loads `valid_credentials.json`, verifies success message
- `testInvalidLogin` — Loads `invalid_credentials.json`, verifies error message

---

#### `CheckboxTest.java` / `DropdownTest.java`

Standard functional tests for checkbox toggling and dropdown selection, demonstrating the framework works with non-healing scenarios too.

---

## 7. Self-Healing Flow (End-to-End)

The complete journey when a broken selector is encountered:

```
TEST: loginPage.enterUsername("tomsmith")
  │
  ▼
SmartElement.fill("tomsmith")
  │ basePage is set → routes to BasePage
  ▼
BasePage.smartFill(SmartElement element, "tomsmith")
  │
  ▼
smartFillInternal("input[data-qa='user-login-field']", "tomsmith", element)
  │
  ├── STEP 1: Try original selector
  │   waitForVisible(By.cssSelector("input[data-qa='user-login-field']"))
  │   → TimeoutException after 10s
  │
  ├── STEP 2: Check healing enabled → YES
  │
  ├── STEP 3: Build Focus HTML
  │   ├── Try direct match with failed selector → no elements found
  │   ├── Derive candidates: *[data-qa='user-login-field']
  │   ├── Try heuristic matches → no elements found
  │   └── Return null (no focus HTML available)
  │
  ├── STEP 4: Capture Screenshot
  │   ├── TakesScreenshot.getScreenshotAs(BYTES)
  │   ├── Save to logs/screenshots/20260216_143022_001_fill_input_data-qa__user-login-field__.png
  │   └── Return Base64-encoded string
  │
  ├── STEP 5: Call AIHealingEngine.healMultiple()
  │   ├── Cache check → miss
  │   ├── HtmlCleaner.cleanHtml(pageSource) → remove scripts, styles, SVGs
  │   ├── HtmlRedactor.redact(cleanedHtml) → redact passwords, tokens, emails
  │   ├── buildPrompt() → assemble prompt with selector, HTML, screenshot reference
  │   ├── GeminiClient.sendPrompt(prompt, screenshotBase64)
  │   │   ├── Build multimodal request (text + image)
  │   │   ├── POST to Gemini API
  │   │   └── Parse response: extract text from candidates[0].content.parts[0].text
  │   ├── Strip markdown fences from response
  │   ├── parseMultipleSelectors() → ["#username", "input[name='username']", "input#username", ...]
  │   ├── Cache first suggestion: "input[data-qa='user-login-field']" → "#username"
  │   └── Return 5 suggestions
  │
  ├── STEP 6: Validate Suggestions Sequentially
  │   ├── Suggestion 1: "#username"
  │   │   WebDriverWait(3s).until(visibilityOfElementLocated(By.cssSelector("#username")))
  │   │   → Element found! isEnabled() → true
  │   │   element.clear(); element.sendKeys("tomsmith")
  │   │   → SUCCESS (winningIdx = 1)
  │   └── (Remaining suggestions skipped)
  │
  ├── STEP 7: Record HealingResult
  │   HealingContext.record(HealingResult{
  │     testName="testLoginWithBrokenSelectors",
  │     originalSelector="input[data-qa='user-login-field']",
  │     healedSelector="#username",
  │     winningIndex=1,
  │     outcome=HEALED,
  │     aiProvider="GEMINI",
  │     healingDurationMs=2340
  │   })
  │
  └── STEP 8: Persist Healed Selector
      ├── SmartElement.updateSelector("#username") → in-memory update
      ├── config.isAutoUpdateCode() → true
      ├── SelectorCodeUpdater.updateAnnotatedSelector(
      │     HealingDemoLoginPage.class, "usernameField",
      │     "input[data-qa='user-login-field']", "#username")
      │   ├── Resolve: src/main/java/com/automation/pages/HealingDemoLoginPage.java
      │   ├── Backup: .selector_backups/HealingDemoLoginPage.java.20260216_143024_567.bak
      │   ├── Comment out old @SeleniumSelector line
      │   └── Insert new @SeleniumSelector(value = "#username")
      └── Log: "[AI-HEAL] Source code auto-updated"
```

---

## 8. Multi-Locator Healing Strategy

The framework uses a **ranked multi-suggestion** approach rather than relying on a single AI suggestion:

```
AI returns 5 suggestions (ordered by AI confidence):
  1. #username                              ← CSS ID selector
  2. input[name='username']                 ← CSS attribute selector
  3. xpath=//input[@id='username']           ← XPath ID selector
  4. input[type='text'][name='username']     ← CSS compound selector
  5. xpath=//form[@id='login']//input[1]     ← XPath positional

Validation sequence:
  Try #1 → Found + clickable → WIN (stop)
  Try #2 → (skipped)
  Try #3 → (skipped)
  ...

If #1 had failed:
  Try #1 → FAIL
  Try #2 → Found + clickable → WIN (stop)
  ...

If all fail:
  Throw NoSuchElementException with message:
  "AI healing exhausted all suggestions for selector: ..."
```

**Benefits:**
- Higher overall success rate than single-suggestion healing
- Mix of CSS and XPath provides resilience against different DOM structures
- Validation wait is reduced to 3 seconds per suggestion (vs. 10 seconds for initial attempt)
- First working suggestion is cached, avoiding re-healing

---

## 9. LLM Provider Architecture

```
                    ┌─────────────────┐
                    │  LLMClient      │  <<interface>>
                    │  (Strategy)     │
                    ├─────────────────┤
                    │ sendPrompt(str) │
                    │ sendPrompt(     │
                    │   str, img)     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼───────┐ ┌───▼──────────┐ ┌─▼──────────────┐
     │ GeminiClient   │ │ OpenAIClient │ │ ClaudeClient   │
     ├────────────────┤ ├──────────────┤ ├────────────────┤
     │ Generative     │ │ Chat         │ │ Messages API   │
     │ Language API   │ │ Completions  │ │                │
     │ v1beta         │ │ API          │ │                │
     │                │ │              │ │                │
     │ gemini-2.0-    │ │ gpt-5.2      │ │ claude-sonnet- │
     │ flash          │ │              │ │ 4-20250514     │
     │                │ │              │ │                │
     │ Vision:        │ │ Vision:      │ │ Vision:        │
     │ inline_data    │ │ image_url    │ │ image source   │
     │ {mime, data}   │ │ data URI     │ │ {base64, data} │
     └────────────────┘ └──────────────┘ └────────────────┘
```

**All three providers share:**
- Configuration resolution: system property > `.env` > environment variable > default
- `HttpClient` with 30s connect timeout
- 90s request timeout
- Multimodal vision support (text + screenshot)
- Screenshot size guard (2MB base64 limit) with text-only fallback
- Identical system prompt content
- Jackson `ObjectMapper` for JSON serialization

---

## 10. Prompt Engineering

The prompt sent to the LLM is carefully structured for maximum accuracy:

```
The selector 'input[data-qa="user-login-field"]' failed.
Task: return 5 replacement selectors (numbered 1-5) that match the intended element.
Mix CSS and XPath. Prefix XPath with xpath=
Output ONLY the numbered list. No markdown, no explanation.

SCREENSHOT:
- A screenshot is attached to this request.
- Use it to visually identify the intended element, then map it to the HTML.

FOCUS_HTML (most relevant snippet):
<div id="content">
  <div id="login">
    <form id="login" ...>
      <div class="row"><input type="text" name="username" id="username">...</div>
    </form>
  </div>
</div>

FULL_PAGE_HTML:
<html>...(cleaned, redacted, truncated to max.html.chars)...</html>
```

**System Prompt (shared across all providers):**
```
You are an expert UI test automation assistant.

Your task: Given a failed selector, FULL_PAGE_HTML, and optionally a screenshot,
return replacement selectors that match the intended element.

Output rules (MUST FOLLOW):
- Return EXACTLY 5 alternative selectors, one per line, numbered 1-5.
- Mix of CSS selectors and XPath selectors.
- Prefer CSS. Use XPath only if CSS is impractical.
- If you return XPath, prefix it with: xpath=
- Do NOT include markdown, backticks, quotes, explanations, or extra whitespace.

Selector quality rules:
- Prefer stable attributes: data-testid, data-test, aria-label, role, name,
  placeholder, type, href, for, title.
- Avoid brittle selectors: nth-child, deeply nested chains, dynamic ids/classes
  (random hashes), absolute XPaths.
- Make it as specific as needed to be unique, but keep it robust.
```

---

## 11. Caching Strategy

```
Request comes in for failedSelector "input[data-qa='user-login-field']"
  │
  ├── Cache Lookup: healing-cache.json
  │   {
  │     "input[data-qa='user-login-field']": "#username",
  │     "#pass-field-old": "#password",
  │     "button.btn-primary-login": "button[type='submit']"
  │   }
  │
  ├── HIT → return List.of("#username") immediately
  │         (outcome = CACHE_HIT, no API call)
  │
  └── MISS → proceed with LLM call
              → on success, cache first suggestion:
              healingCache.put("input[data-qa='user-login-field']", "#username")
              → persist to disk atomically
```

**Cache file format:** Simple JSON map (`String -> String`).

**Cache invalidation:** Manual only (delete the file). The cache does not expire automatically since selectors don't typically change frequently.

---

## 12. Source Code Auto-Patching

When `healing.auto.update.code` is `true`, the framework permanently fixes broken selectors in the source code:

```
Before healing:
─────────────────────────────────────────
    @SeleniumSelector(value = "input[data-qa='user-login-field']")
    private SmartElement usernameField;

After healing:
─────────────────────────────────────────
    // @SeleniumSelector(value = "input[data-qa='user-login-field']")
    @SeleniumSelector(value = "#username")
    private SmartElement usernameField;
```

**Requirements for auto-patching:**
1. The `SmartElement` must have been created via `@SeleniumSelector` (not inline selectors)
2. The `SmartElement` must carry `ownerPageClass` and `fieldName` metadata
3. The source file must be resolvable at `src/main/java/...`
4. The current selector in the file must match the expected old selector
5. A backup is always created before modification

**Backup Location:** `src/main/java/com/automation/pages/.selector_backups/HealingDemoLoginPage.java.20260216_143024_567.bak`

---

## 13. HTML Processing Pipeline

Raw page HTML goes through a multi-stage processing pipeline before reaching the AI:

```
driver.getPageSource()
  │
  ▼ (Stage 1: Noise Removal)
HtmlCleaner.cleanHtml()
  ├── Remove <script> tags and content
  ├── Remove <style> tags and content
  ├── Remove <svg> tags and content
  ├── Remove <noscript> tags
  ├── Remove <meta> and <link> tags
  ├── Remove HTML comments
  ├── Remove inline event handlers (onclick, onload, etc.)
  ├── Remove random data-* attributes (hashes like data-v-a1b2c3d4)
  └── Collapse excessive whitespace
  │
  ▼ (Stage 2: Security Redaction)
HtmlRedactor.redact()
  ├── Redact password input values
  ├── Redact JWT-like tokens
  ├── Redact email addresses (preserve domain)
  └── Redact generic value attributes
  │
  ▼ (Stage 3: Truncation)
Truncate to max.html.chars (default: 200,000)
  │
  ▼ (Stage 4: Focus HTML - separate channel)
BasePage.buildFocusHtml()
  ├── Direct element match with parent context
  ├── Heuristic attribute matching
  ├── Computed CSS styles
  └── Truncate to 20,000 chars
  │
  ▼
Prompt Assembly (redacted HTML + focus HTML + screenshot reference)
```

---

## 14. Screenshot & Multimodal Vision

**Capture Trigger:** Whenever a selector fails and healing mode is entered.

**Process:**
1. Check `screenshot.enabled` configuration
2. Cast driver to `TakesScreenshot`
3. Capture full-page screenshot as `byte[]`
4. Save to disk at `logs/screenshots/{timestamp}_{action}_{selector}.png`
5. Encode to Base64 string
6. Pass to LLM as multimodal input

**Screenshot File Naming:** `20260216_143022_001_fill_input_data-qa__user-login-field__.png`
- Timestamp (millisecond precision)
- Action type (`click` or `fill`)
- Sanitized selector (non-alphanumeric chars replaced with `_`)

**Vision Support by Provider:**
| Provider | Vision Format | Max Base64 Size |
|---|---|---|
| Gemini | `inline_data` with `mime_type` and `data` | 2,000,000 chars |
| OpenAI | `image_url` with `data:image/png;base64,...` data URI | 2,000,000 chars |
| Claude | `image` block with `source.type=base64` | 2,000,000 chars |

**Fallback:** If the screenshot exceeds the size limit, healing proceeds with text-only (no image).

---

## 15. Security Architecture

### API Key Management
- API keys (`GEMINI_API_KEY`, `OPENAI_API_KEY`, `CLAUDE_API_KEY`) are stored in `.env` file
- `.env` is listed in `.gitignore` — never committed to version control
- Fallback to system environment variables
- Keys are never logged or included in reports

### HTML Redaction (Before AI Transmission)
- Password input values → `[REDACTED_PASSWORD]`
- JWT-like tokens → `[REDACTED_TOKEN]`
- Email addresses → `[REDACTED_EMAIL]@domain.com`
- Generic `value` attributes → `[REDACTED]`

### Audit Logging
- All AI interactions logged to dedicated audit file (`logs/ai-healing-audit.log`)
- Prompt content logged (first 2,000 chars only) — after redaction
- Separate from application log for compliance review

### File System Safety
- Source code auto-patching always creates a backup before modification
- Cache persistence uses atomic file moves to prevent corruption
- Screenshot directory is auto-created with proper parent paths

---

## 16. Logging & Audit Trail

### Log4j2 Configuration

**Three Appenders:**

| Appender | Target | Rolling Policy |
|---|---|---|
| `Console` | `SYSTEM_OUT` | None (live output) |
| `AIHealingAudit` | `logs/ai-healing-audit.log` | Time-based (daily) + Size-based (10MB), max 10 files |
| `File` | `logs/application.log` | Time-based (daily) + Size-based (10MB), max 10 files |

**Two Logger Categories:**

| Logger | Level | Appenders | Additivity |
|---|---|---|---|
| `AIHealingAudit` | INFO | AIHealingAudit + Console | `false` |
| Root | INFO | Console + File | — |

**Log Pattern:** `%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n`

### What Gets Logged

| Event | Logger | Level |
|---|---|---|
| Healing attempt started | AIHealingAudit | INFO |
| Failed selector details | AIHealingAudit | INFO |
| Screenshot attached (yes/no) | AIHealingAudit | INFO |
| Prompt length and preview | AIHealingAudit | INFO |
| Each AI suggestion | AIHealingAudit | INFO |
| Healing success/failure | AIHealingAudit | INFO/WARN |
| Source code auto-update | Application | WARN |
| Suite start/end with stats | Both | INFO |
| LLM API errors | Application | ERROR |

---

## 17. Reporting System

### Console Report (at suite end)

```
╔══════════════════════════════════════════════════════════════╗
║               AI SELF-HEALING REPORT SUMMARY                ║
╠══════════════════════════════════════════════════════════════╣
║  Total Healing Events:    6                                  ║
║  Passed with Healing:     5                                  ║
║  Failed (Healing Tried):  1                                  ║
║  Skipped:                 0                                  ║
╚══════════════════════════════════════════════════════════════╝

  [HEALED] HealingDemoLoginPage#testLoginWithBrokenSelectors | input[data-qa='user-login-field'] -> #username
  [HEALED] HealingDemoLoginPage#testLoginWithBrokenSelectors | #pass-field-old -> #password
  ...
```

### HTML Report

**Location:** `target/surefire-reports/ai-healing-report.html`

**Layout:**
- Header with title and generation timestamp
- Four summary cards: Total (purple), Healed (green), Failed (red), Skipped (amber)
- Detailed results table with all healing events
- Winning suggestions highlighted in bold with green background
- Responsive, modern design (no external CSS framework required)

---

## 18. Configuration Management

### Configuration Files

**`config.properties`** — Browser/driver settings:
```properties
browser.type=chromium
browser.headless=false
healing.enabled=true
ai.provider=GEMINI
```

**`healing-config.yaml`** — AI healing settings:
```yaml
healing:
  enabled: true
  retry.count: 5
  auto.update.code: true
  max.html.chars: 200000
ai:
  provider: GEMINI
  gemini:
    model: gemini-2.0-flash
  openai:
    model: gpt-4o
  claude:
    model: claude-sonnet-4-20250514
screenshot:
  enabled: true
  directory: logs/screenshots
dom:
  simplified: true
  capture.css: true
```

**`.env`** — Secrets (git-ignored):
```
GEMINI_API_KEY=your_key_here
OPENAI_API_KEY=your_key_here
CLAUDE_API_KEY=your_key_here
```

### Override Hierarchy

```
System Properties (-D flags)    ← Highest priority
        ↓
healing-config.yaml
        ↓
config.properties
        ↓
.env file
        ↓
Environment variables
        ↓
Hard-coded defaults              ← Lowest priority
```

---

## 19. Dependency Management

### Maven Dependencies (pom.xml)

| Dependency | Version | Purpose |
|---|---|---|
| `selenium-java` | 4.40.0 | Browser automation |
| `testng` | 7.8.0 | Test framework |
| `jackson-databind` | 2.15.2 | JSON serialization/deserialization |
| `lombok` | 1.18.30 | Boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`, `@Getter`) |
| `log4j-core` | 2.21.1 | Logging implementation |
| `log4j-api` | 2.21.1 | Logging API |
| `log4j-slf4j2-impl` | 2.21.1 | SLF4J bridge to Log4j2 |
| `dotenv-java` | 3.0.0 | `.env` file loading |

### Maven Plugins

| Plugin | Version | Purpose |
|---|---|---|
| `maven-compiler-plugin` | 3.11.0 | Java 17 compilation |
| `maven-surefire-plugin` | 3.2.2 | Test execution using `testng.xml` |

### Notable Absent Dependencies
- **No SnakeYAML** — Custom minimal YAML parser in `HealingConfig`
- **No Jsoup** — HTML processing uses regex-based cleaning (lighter weight)
- **No external HTTP library** — Uses Java 11+ `java.net.http.HttpClient`

---

## 20. Test Data Architecture

Test data is externalized in JSON files under `src/test/resources/data/`:

```
data/
├── user.json                    # Shared user credentials
├── login/
│   ├── valid_credentials.json   # Valid login + expected success message
│   └── invalid_credentials.json # Invalid login + expected error message
├── forms/
│   └── contact_form.json        # Form field values
├── healing/
│   └── broken_selectors.json    # Documents broken vs correct selectors
└── environments/
    └── staging.json             # Base URL + page paths
```

**Loading Pattern:**
```java
User user = JsonUtils.getTestData("data/user.json", User.class);
```

`JsonUtils` checks the file system first, then falls back to classpath — works in both IDE and Maven builds.

---

## 21. WebDriver Lifecycle Management

```
Test Suite Start
    │
    ├── @BeforeClass (first test class)
    │   SeleniumFactory.getInstance()
    │     ├── loadConfig() → config.properties
    │     ├── initializeDriver() → ChromeDriver
    │     └── Return singleton instance
    │
    ├── Test execution (multiple test classes)
    │   ├── HealingDemoTest → uses shared driver
    │   ├── LoginTest → uses shared driver
    │   ├── CheckboxTest → uses shared driver
    │   └── DropdownTest → uses shared driver
    │
    └── @AfterSuite (last cleanup)
        SeleniumFactory.close()
          ├── driver.quit()
          ├── driver = null
          └── instance = null
```

**Key Design Decision:** The `SeleniumFactory` singleton is shared across all test classes. Individual test classes should NOT close the driver (only the last test in the suite does so via `@AfterSuite`). This prevents browser restart overhead between test classes.

**Implicit Wait:** Set to `Duration.ZERO` to ensure explicit waits (`WebDriverWait`) work correctly without interference.

---

## 22. Thread Safety Model

| Component | Thread Safety Mechanism | Scope |
|---|---|---|
| `SeleniumFactory` | `synchronized` on `getInstance()` and `close()` | Singleton access |
| `HealingConfig` | `volatile` instance + double-checked locking | Singleton access |
| `HealingContext` | `CopyOnWriteArrayList` | Result collection |
| `HealingCache` | `synchronized` on all public methods | Cache read/write |
| `HtmlRedactor` | Stateless (final class, no instances) | N/A |
| `HtmlCleaner` | Stateless (static methods, compiled Patterns) | N/A |

**Current Limitation:** TestNG suite is configured with `parallel="none"` and `thread-count="1"`. Multi-threaded test execution would require a per-thread WebDriver model (ThreadLocal) rather than the current singleton.

---

## 23. Error Handling Strategy

### Layered Error Handling

| Layer | Strategy |
|---|---|
| **SmartElement** | If `basePage` is null, falls back to standard Selenium (no healing) |
| **BasePage** | Catches `NoSuchElementException`, `TimeoutException`, `StaleElementReferenceException` → enters heal mode. All other exceptions propagate normally. |
| **AIHealingEngine** | Catches all LLM call exceptions → logs error, returns empty list. Never lets AI failures crash tests. |
| **LLM Clients** | Catch HTTP/parsing exceptions → return `null`. Log errors but don't throw. |
| **HealingCache** | Catch file I/O exceptions → start with empty cache, log warning. |
| **SelectorCodeUpdater** | Catch all exceptions → return `false`, log warning. Source patching failure never fails a test. |
| **AiHealingListener** | Catches all exceptions in `onTestFailure` → records SKIPPED result, never interferes with TestNG. |
| **Screenshot Capture** | Catches all exceptions → returns `null`. Screenshot failure never fails healing. |

### Graceful Degradation Chain

```
Full healing (multimodal) ─── screenshot too large ──→ Text-only healing
Text-only healing ────────── LLM returns null ──────→ Parse empty list
Parse empty list ─────────── no suggestions ────────→ All suggestions fail
All suggestions fail ─────── exhausted ─────────────→ Throw NoSuchElementException
                                                        (test fails normally)
```

The system **never causes a test to fail in a way it wouldn't have failed without healing**. At worst, healing doesn't help and the original exception propagates.

---

## 24. Data Flow Diagrams

### Healing Data Flow

```
    ┌─────────┐
    │  Test    │─── Uses ──→ ┌───────────┐
    │  Class   │             │ Page      │
    └─────────┘             │ Object    │
                             └─────┬─────┘
                                   │
                           smartClick/smartFill
                                   │
                             ┌─────▼──────┐
                             │  BasePage   │
                             └─────┬──────┘
                                   │ (on failure)
                    ┌──────────────┼──────────────┐
                    │              │              │
              ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼──────┐
              │ Focus HTML│ │Screenshot │ │AIHealing   │
              │ Builder   │ │ Capture   │ │Engine      │
              └───────────┘ └───────────┘ └─────┬──────┘
                                                │
                              ┌─────────────────┼─────────────────┐
                              │                 │                 │
                        ┌─────▼──────┐  ┌───────▼───────┐ ┌──────▼──────┐
                        │ HTML       │  │ Healing       │ │ LLM Client  │
                        │ Processing │  │ Cache         │ │ (Strategy)  │
                        │ Pipeline   │  │               │ │             │
                        └────────────┘  └───────────────┘ └──────┬──────┘
                                                                 │
                                                    ┌────────────┼────────────┐
                                                    │            │            │
                                              ┌─────▼───┐ ┌─────▼───┐ ┌─────▼───┐
                                              │ Gemini  │ │ OpenAI  │ │ Claude  │
                                              │ API     │ │ API     │ │ API     │
                                              └─────────┘ └─────────┘ └─────────┘
```

### TestNG Listener Data Flow

```
    TestNG Engine
        │
        ├── onTestFailure ──→ AiHealingListener
        │                      ├── isLocatorFailure?
        │                      ├── extractDriver (reflection)
        │                      ├── buildDiagnosticCapture
        │                      ├── healingEngine.healMultiple()
        │                      ├── validateSelector()
        │                      └── HealingContext.record()
        │
        ├── retry? ──────────→ AiHealingRetryAnalyzer
        │                      └── max 1 retry for locator failures
        │
        ├── transform ───────→ RetryTransformer
        │                      └── auto-apply retry analyzer
        │
        └── generateReport ──→ HealingReportListener
                                ├── HealingContext.getResults()
                                ├── printConsoleSummary()
                                └── generateHtmlReport()
```

---

## 25. Class Relationship Diagram

```
                        ┌─────────────────────────┐
                        │     <<interface>>        │
                        │      LLMClient           │
                        └────────────┬────────────┘
                                     │ implements
                  ┌──────────────────┼──────────────────┐
                  │                  │                  │
          ┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
          │ GeminiClient │  │ OpenAIClient │  │ ClaudeClient │
          └──────────────┘  └──────────────┘  └──────────────┘
                  ▲                  ▲                  ▲
                  └──────────────────┼──────────────────┘
                                     │ uses
                          ┌──────────▼──────────┐
                          │  AIHealingEngine     │
                          │  ├── LLMClient       │
                          │  ├── HealingCache    │
                          │  └── HealingConfig   │
                          └──────────┬──────────┘
                                     │ uses
                          ┌──────────▼──────────┐
                          │    BasePage          │◄─────── abstract
                          │  ├── AIHealingEngine │
                          │  ├── HealingConfig   │
                          │  └── WebDriver       │
                          └──────────┬──────────┘
                                     │ extends
                  ┌──────────────────┼──────────────────┐
                  │                  │                  │
          ┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
          │  LoginPage   │  │HealingDemo   │  │CheckboxPage  │
          │              │  │LoginPage     │  │DropdownPage  │
          └──────────────┘  └──────────────┘  └──────────────┘
                  │                  │
                  │ @SeleniumSelector│
                  ▼                  ▼
          ┌──────────────────────────────────┐
          │         SmartElement              │
          │  ├── WebDriver                   │
          │  ├── By                          │
          │  ├── String selector             │
          │  ├── BasePage (back-reference)   │
          │  ├── Class<?> ownerPageClass     │
          │  └── String fieldName            │
          └──────────────────────────────────┘
                  ▲
                  │ creates
          ┌───────────────────┐
          │ElementInitializer │── reads ──→ @SeleniumSelector
          └───────────────────┘

          ┌───────────────────┐
          │HealingContext     │── stores ──→ HealingResult
          └───────┬───────────┘                  │
                  ▲                              │ contains
                  │ records                      ▼
          ┌───────────────────┐         DiagnosticCapture
          │AiHealingListener  │
          │  (ITestListener)  │
          └───────────────────┘

          ┌───────────────────┐
          │HealingReport      │── reads ──→ HealingContext
          │Listener (IReporter│── generates → HTML Report
          └───────────────────┘

          ┌───────────────────┐
          │RetryTransformer   │── applies → AiHealingRetryAnalyzer
          │(IAnnotation       │              (IRetryAnalyzer)
          │ Transformer)      │
          └───────────────────┘
```

---

## 26. Configuration Reference

### Complete Configuration Matrix

| Property | File | Default | Type | Description |
|---|---|---|---|---|
| `browser.type` | config.properties | `chrome` | String | Browser: chrome, chromium, firefox, edge |
| `browser.headless` | config.properties | `true` | boolean | Run browser in headless mode |
| `healing.enabled` | healing-config.yaml | `true` | boolean | Master switch for AI healing |
| `healing.retry.count` | healing-config.yaml | `5` | int (1-5) | Number of AI suggestions to request |
| `healing.auto.update.code` | healing-config.yaml | `true` | boolean | Auto-patch source code with healed selectors |
| `healing.max.html.chars` | healing-config.yaml | `200000` | int | Max HTML chars in AI prompt |
| `ai.provider` | healing-config.yaml | `GEMINI` | String | AI provider: GEMINI, OPENAI, CLAUDE |
| `ai.gemini.model` | healing-config.yaml | `gemini-2.0-flash` | String | Gemini model name |
| `ai.openai.model` | healing-config.yaml | `gpt-4o` | String | OpenAI model name |
| `ai.claude.model` | healing-config.yaml | `claude-sonnet-4-20250514` | String | Claude model name |
| `screenshot.enabled` | healing-config.yaml | `true` | boolean | Capture screenshots on failure |
| `screenshot.directory` | healing-config.yaml | `logs/screenshots` | String | Screenshot save directory |
| `dom.simplified` | healing-config.yaml | `true` | boolean | Use simplified DOM for AI |
| `dom.capture.css` | healing-config.yaml | `true` | boolean | Capture computed CSS styles |

### Environment Variables / .env Keys

| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY` | Google Gemini API key |
| `OPENAI_API_KEY` | OpenAI API key |
| `CLAUDE_API_KEY` | Anthropic Claude API key |
| `GEMINI_MODEL` | Override Gemini model name |
| `OPENAI_MODEL` | Override OpenAI model name |
| `CLAUDE_MODEL` | Override Claude model name |
| `GEMINI_SYSTEM_PROMPT` | Override Gemini system prompt |
| `OPENAI_SYSTEM_PROMPT` | Override OpenAI system prompt |
| `CLAUDE_SYSTEM_PROMPT` | Override Claude system prompt |
| `GEMINI_IMAGE_MAX_BASE64_CHARS` | Max screenshot size for Gemini |
| `OPENAI_IMAGE_MAX_BASE64_CHARS` | Max screenshot size for OpenAI |
| `CLAUDE_IMAGE_MAX_BASE64_CHARS` | Max screenshot size for Claude |

### System Property Overrides (-D flags)

| Property | Override For |
|---|---|
| `-Dhealing.enabled` | healing.enabled |
| `-Dhealing.retry.count` | healing.retry.count |
| `-Dhealing.auto.update.code` | healing.auto.update.code |
| `-Dhealing.max.html.chars` | healing.max.html.chars |
| `-Dai.provider` | ai.provider |
| `-Dhealing.screenshot.enabled` | screenshot.enabled |
| `-Dhealing.dom.simplified` | dom.simplified |
| `-Dhealing.dom.capture.css` | dom.capture.css |
| `-Dhealing.cache.path` | Cache file location |
| `-Dgemini.model` | Gemini model |
| `-Dopenai.model` | OpenAI model |
| `-Dclaude.model` | Claude model |
| `-Dgemini.systemPrompt` | Gemini system prompt |
| `-Dopenai.systemPrompt` | OpenAI system prompt |
| `-Dclaude.systemPrompt` | Claude system prompt |

---

## 27. Extension Points

The framework is designed with multiple extension points:

### Adding a New LLM Provider
1. Create a new class implementing `LLMClient`
2. Implement `sendPrompt(String)` and optionally `sendPrompt(String, String)` for vision
3. Add a case to `AIHealingEngine.defaultClient()` factory method
4. Add model configuration to `healing-config.yaml` and `HealingConfig`

### Adding a New Page Object
1. Create a class extending `BasePage`
2. Annotate element fields with `@SeleniumSelector`
3. `ElementInitializer` handles wiring automatically via reflection
4. All healing capabilities are inherited from `BasePage`

### Adding a New Browser
1. Add a case to `SeleniumFactory.initializeDriver()`
2. Create a `createXxxDriver(boolean headless)` method
3. Add browser-specific options

### Custom Healing Strategy
- Replace `AIHealingEngine` with a custom implementation
- Inject via `BasePage` constructor (would require modification)
- Or implement a different `LLMClient` with custom logic

### Custom Report Format
- Implement `IReporter` interface
- Register in `testng.xml` as a listener
- Read from `HealingContext.getResults()`

---

## 28. Known Limitations & Trade-offs

| Limitation | Impact | Mitigation |
|---|---|---|
| Single-threaded execution only | Cannot parallelize tests | Migrate to ThreadLocal WebDriver for parallel support |
| YAML parser is minimal (2-level nesting) | Cannot handle complex YAML structures | Sufficient for current needs; add SnakeYAML if needed |
| HTML cleaning is regex-based | May miss edge cases in complex HTML | Works well in practice; add Jsoup if precision needed |
| Source code patching assumes Maven layout | Won't work with non-standard source paths | `resolveSourceFile` can be overridden |
| Cache has no TTL/expiration | Stale cached selectors may be returned | Manual cache invalidation (delete file) |
| Implicit wait set to ZERO globally | Some legacy tests might break | By design — explicit waits are preferred |
| LLM API calls add latency (2-10s per heal) | Slows test execution during healing | Caching eliminates repeat API calls |
| Screenshots may be large (1-2MB base64) | May exceed API limits | Size guard with text-only fallback |
| Redaction is best-effort | May miss some sensitive data | Not a DLP solution; review before production use |
| `AiHealingListener` uses reflection for driver | May fail with non-standard test architectures | Returns null and records SKIPPED |
| No support for Shadow DOM | Cannot heal selectors inside Shadow DOM | Extend `toBy()` with Shadow DOM traversal |

---

## Appendix A: TestNG Suite Configuration

```xml
<suite name="AI Self-Healing Test Suite" parallel="none" thread-count="1">
    <listeners>
        <listener class-name="com.automation.listeners.AiHealingListener"/>
        <listener class-name="com.automation.listeners.HealingReportListener"/>
        <listener class-name="com.automation.listeners.RetryTransformer"/>
    </listeners>
    <test name="AI Healing Demo Tests">
        <classes><class name="com.automation.tests.HealingDemoTest"/></classes>
    </test>
    <test name="Login Tests">
        <classes><class name="com.automation.tests.LoginTest"/></classes>
    </test>
    <test name="Checkbox Tests">
        <classes><class name="com.automation.tests.CheckboxTest"/></classes>
    </test>
    <test name="Dropdown Tests">
        <classes><class name="com.automation.tests.DropdownTest"/></classes>
    </test>
</suite>
```

---

## Appendix B: Build & Run Commands

```bash
# Run all tests
mvn clean test

# Run with healing disabled
mvn clean test -Dhealing.enabled=false

# Run with a different AI provider
mvn clean test -Dai.provider=OPENAI

# Run with custom retry count
mvn clean test -Dhealing.retry.count=3

# Run headless
# Edit config.properties: browser.headless=true

# Run with screenshots disabled
mvn clean test -Dhealing.screenshot.enabled=false
```

---

## Appendix C: Generated Artifacts

| Artifact | Path | Description |
|---|---|---|
| AI Healing HTML Report | `target/surefire-reports/ai-healing-report.html` | Visual healing report |
| Application Log | `logs/application.log` | General application logs |
| AI Healing Audit Log | `logs/ai-healing-audit.log` | Dedicated AI interaction audit trail |
| Screenshots | `logs/screenshots/*.png` | Failure screenshots |
| Healing Cache | `healing-cache.json` | Persistent selector cache |
| Source Backups | `src/main/java/.../pages/.selector_backups/*.bak` | Pre-patch backups |

---

*This document provides a complete architectural reference for the Java Selenium AI Self-Healing Test Framework. Every class, method, configuration option, design pattern, and data flow has been documented to enable full understanding of the system.*
