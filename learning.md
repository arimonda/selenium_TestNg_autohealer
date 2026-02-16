### Learning notes — Java Selenium Framework (with AI selector healing)

This document explains **the working principle of the framework**, plus **each class and its methods** (what they do, how they interact, and how execution flows during a test run).

---

### What this framework is

At a high level, this is a **Selenium + TestNG** automation framework with:

- **Driver lifecycle management** via a singleton (`SeleniumFactory`)
- **Page Object Model (POM)** via `BasePage` and concrete pages like `LoginPage`
- **Annotation-driven element wiring** using `@SeleniumSelector` + `ElementInitializer`
- **Smart actions** (`smartClick`, `smartFill`) that wrap waits and can attempt **AI-based selector healing**
- **Optional AI healing** that calls an LLM (default `OpenAIClient`) and caches healed selectors (`HealingCache`)
- **Logging + audit trail** via `log4j2.xml` (including a dedicated AI healing audit log)

---

### Project structure (important folders/files)

- **`src/main/java/com/automation/base/`**
  - `SeleniumFactory`: creates and owns the `WebDriver`
  - `BasePage`: common page behaviors + “smart actions” + healing hooks
- **`src/main/java/com/automation/pages/`**
  - `LoginPage`: example page object
  - `SeleniumSelector`: annotation for element selectors
  - `ElementInitializer`: reflection-based element injection
- **`src/main/java/com/automation/utils/`**
  - `SmartElement`: wrapper around `By` + optional smart actions through `BasePage`
  - `JsonUtils`: loads test data JSON
  - `HtmlCleaner`: reduces HTML noise before LLM prompt
- **`src/main/java/com/automation/ai/`**
  - `AIHealingEngine`: orchestration for healing (cache → prompt → LLM)
  - `OpenAIClient`: concrete `LLMClient` that calls OpenAI Chat Completions API
  - `LLMClient`: interface for pluggable LLM providers
  - `HealingCache`: persistent map of failedSelector → healedSelector
  - `HtmlRedactor`: redacts sensitive values before sending HTML to the LLM
- **`src/test/java/com/automation/tests/`**
  - `LoginTest`: example TestNG tests that use the framework
- **`src/main/resources/`**
  - `config.properties`: browser/headless config
  - `log4j2.xml`: logging configuration (console, file, AI audit log)
- **`src/test/resources/data/`**
  - `user.json`: example test data
- **Root**
  - `testng.xml`: TestNG suite definition
  - `healing-cache.json` (generated at runtime): healing cache (default path)

---

### End-to-end working principle (what happens when you run tests)

Here’s the typical runtime flow using `LoginTest` + `LoginPage`:

1. **TestNG starts**
   - `testng.xml` defines the suite and which test classes run.

2. **Test setup creates the driver**
   - `LoginTest.setUp()` calls `SeleniumFactory.getInstance()`.
   - `SeleniumFactory` reads `src/main/resources/config.properties` and creates a driver:
     - Chrome/Chromium (`ChromeDriver`)
     - Edge (`EdgeDriver`)
     - Firefox (`FirefoxDriver`)
   - It sets `implicitlyWait(Duration.ZERO)` (so waits are explicit and predictable).

3. **Page object is created and its elements are wired**
   - `new LoginPage(driver)` calls `super(driver)` (constructor of `BasePage`).
   - `BasePage`:
     - Stores the `driver`
     - Creates an `AIHealingEngine`
     - Calls `ElementInitializer.initElements(driver, this)`
   - `ElementInitializer` uses reflection:
     - Finds fields annotated with `@SeleniumSelector`
     - Creates a `SmartElement` for each field (with selector + `By`)
     - If the page is a `BasePage`, `SmartElement` is created with a reference to that `BasePage`
       - This is how `SmartElement.click()` / `fill()` can delegate to `smartClick` / `smartFill`

4. **Test actions**
   - `navigateTo(url)` calls `driver.get(url)`.
   - `enterUsername(...)` calls `usernameField.fill(...)`.
     - Because `usernameField` is a `SmartElement` created with `basePage`, its `fill()` calls `BasePage.smartFill(selector, value)`.

5. **Smart actions (wait + retry + healing)**
   - `BasePage.smartFill(selector, value)`:
     - Converts selector into a Selenium `By` using `toBy(...)`
       - CSS by default
       - XPath when the selector starts with `xpath=`
     - Waits for visibility via `waitForVisible(by)` (10s default)
     - Clears and types into the element
   - If the wait fails (`TimeoutException`):
     - It attempts **AI healing** (up to 1 retry)
     - It validates the healed selector before retrying (must match exactly 1 element and be visible/clickable)

6. **AI Healing (cache → screenshot + focus HTML + full HTML → clean/redact → LLM call → validation)**
   - `BasePage.attemptValidatedHealingForFill/Click(failedSelector)`:
     - Captures a **screenshot on failure** and saves it to `logs/screenshots/` (for debugging + optional vision-based healing)
     - First tries `healingEngine.heal(failedSelector, pageSource, true)` (cache allowed)
     - If invalid, retries once bypassing cache: `healingEngine.heal(..., false)`
   - `AIHealingEngine.heal(failedSelector, html, allowCache)`:
     - If `allowCache`, consults `HealingCache` for an existing healed selector
     - Otherwise:
       - Optionally includes the **screenshot** (vision) to identify the intended element visually
       - Optionally adds a **FOCUS_HTML** section (best-effort DOM snippets near likely matches)
       - Cleans HTML (`HtmlCleaner.cleanHtml`) and redacts sensitive patterns (`HtmlRedactor.redact`)
       - Builds a prompt with:
         - `SCREENSHOT` (attached, when available)
         - `FOCUS_HTML` (small, high-signal snippet when available)
         - `FULL_PAGE_HTML` (cleaned+redacted, best-effort; size cap is configurable)
       - Calls the LLM via `LLMClient.sendPrompt(prompt)`
     - On success, writes result to cache: `HealingCache.put(failedSelector, healedSelector)`

7. **Logging**
   - Regular logs go to console + `logs/application.log`
   - AI healing also logs an audit trail to `logs/ai-healing-audit.log` using logger name `AIHealingAudit`

8. **Suite teardown**
   - `LoginTest.tearDown()` calls `SeleniumFactory.close()` which does `driver.quit()`.

---

### Configuration and “knobs” you can tune

#### Browser configuration (`src/main/resources/config.properties`)

- `browser.type`: `chrome` / `chromium` / `firefox` / `edge`
- `browser.headless`: `true` or `false`

#### AI/LLM configuration (environment, `.env`, or JVM system properties)

The framework can use **OpenAI** or **Gemini** for healing. By default, if `GEMINI_API_KEY` is present it will prefer **Gemini**; otherwise it uses **OpenAI**.

You can force Gemini with: `-Dhealing.llm=gemini`.

`OpenAIClient` looks up settings in this order:

- **Model**
  - JVM: `-Dopenai.model=...`
  - `.env`: `OPENAI_MODEL=...`
  - env var: `OPENAI_MODEL=...`
  - default: `gpt-5.2`

- **System prompt (instruction prompt)**
  - JVM: `-Dopenai.systemPrompt="..."`
  - `.env`: `OPENAI_SYSTEM_PROMPT=...`
  - env var: `OPENAI_SYSTEM_PROMPT=...`
  - default: built-in selector-healing instructions (one-line output, CSS preferred, `xpath=` prefix for XPath)

- **API key**
  - `.env`: `OPENAI_API_KEY=...`
  - env var: `OPENAI_API_KEY=...`

`GeminiClient` configuration:

- **API key**
  - `.env`: `GEMINI_API_KEY=...`
  - env var: `GEMINI_API_KEY=...`

- **Model**
  - JVM: `-Dgemini.model=...`
  - `.env`: `GEMINI_MODEL=...`
  - env var: `GEMINI_MODEL=...`
  - default: `gemini-2.0-flash`

- **System prompt**
  - JVM: `-Dgemini.systemPrompt="..."`
  - `.env`: `GEMINI_SYSTEM_PROMPT=...`
  - env var: `GEMINI_SYSTEM_PROMPT=...`

#### Healing cache path

- JVM: `-Dhealing.cache.path=path/to/healing-cache.json`
- Default: `./healing-cache.json`

---

### Class-by-class and method-by-method explanation

Below, each class is explained with its responsibilities and the behavior of each method.

---

### `com.automation.base.SeleniumFactory`

**Responsibility**: Create and own a single `WebDriver` instance using config from `config.properties`.

**Key fields**
- `private static SeleniumFactory instance`: singleton instance
- `private WebDriver driver`: the current Selenium driver
- `private Properties config`: loaded from `config.properties`

**Methods**
- `private SeleniumFactory()`
  - Calls `loadConfig()` then `initializeDriver()`.
  - Private to enforce singleton usage.

- `public static synchronized SeleniumFactory getInstance()`
  - Creates the singleton on first call, returns it thereafter.
  - `synchronized` ensures safe lazy-init across threads (but the *driver itself* is still shared).

- `private void loadConfig()`
  - Reads `src/main/resources/config.properties`.
  - Throws a runtime error if missing (fail-fast).

- `private void initializeDriver()`
  - Reads:
    - `browser.type` (default `chrome`)
    - `browser.headless` (default `true`)
  - Chooses `createChromeDriver` / `createFirefoxDriver` / `createEdgeDriver`.
  - Sets `implicitlyWait(Duration.ZERO)` to avoid hidden implicit wait behavior.

- `private WebDriver createChromeDriver(boolean headless)`
  - Configures `ChromeOptions` (headless, disable GPU, window size).
  - Returns `new ChromeDriver(options)`.

- `private WebDriver createFirefoxDriver(boolean headless)`
  - Configures `FirefoxOptions` (headless).
  - Returns `new FirefoxDriver(options)`.

- `private WebDriver createEdgeDriver(boolean headless)`
  - Configures `EdgeOptions` (headless, disable GPU, window size).
  - Returns `new EdgeDriver(options)`.

- `public WebDriver getDriver()`
  - Returns current driver, initializes if null.

- `public void close()`
  - Calls `driver.quit()` and sets `driver = null`.

**Notes**
- This is a **single-driver singleton**. It’s simple for demos but not designed for true parallel execution with separate drivers per thread.

---

### `com.automation.base.BasePage`

**Responsibility**: Common base for all Page Objects. Provides:

- navigation
- explicit waits
- smart actions with **AI healing retry** on `TimeoutException`
- selector parsing to CSS or XPath

**Key fields**
- `protected WebDriver driver`
- `private final AIHealingEngine healingEngine`
- `MAX_HEALING_RETRIES = 3` (minimum 3 healing retries; total attempts = 1 initial + up to 3 retries)
- `DEFAULT_WAIT = 10s`
- `HEAL_VALIDATION_WAIT = 2s`

**Methods**
- `protected BasePage(WebDriver driver)`
  - Stores `driver`
  - Creates `AIHealingEngine`
  - Calls `ElementInitializer.initElements(driver, this)` to inject `SmartElement` fields.

- `public void smartClick(String selector)`
  - Waits for element to be clickable, then clicks.
  - On `TimeoutException`:
    - attempts healing and retries once with a validated healed selector.
  - On `StaleElementReferenceException`:
    - retries once without healing (common transient issue).

- `public void smartFill(String selector, String value)`
  - Waits for element to be visible, clears, types.
  - Same retry/healing pattern as `smartClick`.

- `public void navigateTo(String url)`
  - Calls `driver.get(url)`.

- `protected WebElement waitForVisible(By by)`
  - `WebDriverWait(DEFAULT_WAIT).until(visibilityOfElementLocated(by))`

- `protected WebElement waitForClickable(By by)`
  - `WebDriverWait(DEFAULT_WAIT).until(elementToBeClickable(by))`

- `private String attemptValidatedHealingForClick(String failedSelector)`
  - Calls `healingEngine.heal(failedSelector, pageSource, true)`.
  - Validates via `isValidHealedSelectorForClick(...)`.
  - If invalid and there was a candidate, bypasses cache once: `heal(..., false)`.

- `private String attemptValidatedHealingForFill(String failedSelector)`
  - Same pattern as click, but validation checks visibility + enabled.

- `private boolean isValidHealedSelectorForClick(String selector)`
  - Must:
    - parse to `By`
    - match **exactly one element**: `driver.findElements(by).size() == 1`
    - become clickable within `HEAL_VALIDATION_WAIT`

- `private boolean isValidHealedSelectorForFill(String selector)`
  - Must:
    - parse to `By`
    - match exactly one element
    - become visible within `HEAL_VALIDATION_WAIT`
    - be enabled

- `protected By toBy(String selector)`
  - If selector starts with `xpath=`, returns `By.xpath(...)`
  - Else returns `By.cssSelector(...)`

**Why this matters**
- This class is the “engine room” of smart waiting and AI healing.
- The validation step prevents the framework from blindly trusting the LLM output.

---

### `com.automation.pages.SeleniumSelector` (annotation)

**Responsibility**: Marks fields on a Page Object that should be initialized as `SmartElement`.

**Members**
- `String value()`: the selector string (CSS by default; XPath when prefixed with `xpath=`)

---

### `com.automation.pages.ElementInitializer`

**Responsibility**: Reflection-based element initialization.

**Methods**
- `public static void initElements(WebDriver driver, Object pageObject)`
  - Scans `pageObject.getClass().getDeclaredFields()`
  - For each field annotated with `@SeleniumSelector`:
    - reads selector
    - converts to `By` via `toBy(selector)`
    - creates a `SmartElement`
      - If `pageObject` is a `BasePage`, passes it to `SmartElement(...)` so smart actions are available.
    - sets the field value via reflection

- `private static By toBy(String selector)`
  - Same CSS-vs-XPath logic (`xpath=` prefix).

---

### `com.automation.utils.SmartElement`

**Responsibility**: A light wrapper around a locator (`By`) and its original selector string.

It optionally delegates actions to `BasePage` so you automatically get:
- explicit waits
- AI healing retries

**Key fields**
- `WebDriver driver`
- `By by`
- `String selector` (original selector)
- `BasePage basePage` (nullable)
 - `ownerPageClass` + `fieldName` (optional metadata so the framework can persist healed selectors back into the Page Object `.java` file)

**Methods**
- Constructors:
  - `SmartElement(driver, by, selector)` (no smart behavior)
  - `SmartElement(driver, by, selector, basePage)` (enables smart behavior)

- `public WebElement getElement()`
  - Returns `driver.findElement(by)` (no waiting).

- `public void click()`
  - If `basePage != null`: calls `basePage.smartClick(this)` (so BasePage can persist healed selectors)
  - Else: `getElement().click()`

- `public void fill(String value)`
  - If `basePage != null`: calls `basePage.smartFill(this, value)` (so BasePage can persist healed selectors)
  - Else: clear + sendKeys directly

- **Selector persistence (AI auto-update)**
  - When a heal succeeds for a `SmartElement` created from `@SeleniumSelector`, the framework will (best-effort):
    - Comment the old `@SeleniumSelector(...)` line
    - Add a new `@SeleniumSelector(...)` line below with the healed selector
    - Keep a backup copy under `src/main/java/.../.selector_backups/`

- `public String getText()`
  - Returns `getElement().getText()`

- `public boolean isVisible()`
  - Returns `getElement().isDisplayed()` with exception safety

---

### `com.automation.pages.LoginPage`

**Responsibility**: Example Page Object for a login form.

**Fields (wired by `ElementInitializer`)**
- `usernameField`: `input[name='username']`
- `passwordField`: `input[name='password']`
- `loginButton`: `button[type='submit']`

**Methods**
- `public LoginPage(WebDriver driver)`
  - Calls `BasePage` constructor → triggers element injection.

- `public void enterUsername(String username)`
  - Logs and calls `usernameField.fill(username)`

- `public void enterPassword(String password)`
  - Logs and calls `passwordField.fill(password)`

- `public void clickLogin()`
  - Logs and calls `loginButton.click()`

- `public void login(String username, String password)`
  - Convenience method: enterUsername → enterPassword → clickLogin

---

### `com.automation.utils.JsonUtils`

**Responsibility**: Load JSON test data into a POJO.

**Method**
- `public static <T> T getTestData(String fileName, Class<T> clazz)`
  - Tries to load from: `src/test/resources/data/<fileName>`
  - If not found, tries classpath resource: `data/<fileName>`
  - Deserializes using Jackson `ObjectMapper`
  - Throws a runtime exception if not found or parse fails

---

### `com.automation.models.User`

**Responsibility**: Example POJO used for test data.

**Fields**
- `username`, `password`, `email`

**Generated methods**
- Lombok `@Data` generates getters/setters, `toString`, `equals`, `hashCode`.

---

### `com.automation.utils.HtmlCleaner`

**Responsibility**: Reduce HTML noise before building LLM prompts.

**Method**
- `public static String cleanHtml(String html)`
  - Removes `<script>...</script>`, `<style>...</style>`, `<svg>...</svg>`
  - Collapses whitespace to single spaces

---

### `com.automation.ai.HtmlRedactor`

**Responsibility**: Best-effort redaction of sensitive values before:
- sending HTML to the LLM
- logging prompts to an audit log

**Method**
- `public static String redact(String html)`
  - Redacts:
    - password input values
    - JWT-like tokens
    - emails (keeps domain)
    - generic `value="..."` attributes (best-effort, may over-redact)

---

### `com.automation.ai.HealingCache`

**Responsibility**: Persistent cache of healing results.

**Stored mapping**
- `failedSelector -> healedSelector`

**Key fields**
- `Path cachePath`
- `Map<String,String> cache`
- `boolean loaded`

**Methods**
- `public HealingCache(Path cachePath)`
  - Sets the cache file path.

- `public synchronized Optional<String> get(String failedSelector)`
  - Lazy-loads cache file on first call (`ensureLoaded`)
  - Returns cached value (if any)

- `public synchronized void put(String failedSelector, String healedSelector)`
  - Writes to in-memory map, then persists to disk best-effort.

- `public synchronized Map<String, String> snapshot()`
  - Returns an unmodifiable copy (for debugging/inspection).

- `private void ensureLoaded()`
  - Reads JSON from `cachePath` (if exists) once.
  - Keeps an in-memory `HashMap`.

- `private void persistBestEffort()`
  - Writes JSON to `<file>.tmp`, then moves to the real file.
  - Tries `ATOMIC_MOVE`, and if not supported (common on Windows/FS), retries without atomic move.

---

### `com.automation.ai.LLMClient` (interface)

**Responsibility**: Abstraction point for any LLM provider.

**Method**
- `String sendPrompt(String prompt)`
  - Contract: return a raw selector string (CSS or `xpath=...`).

---

### `com.automation.ai.OpenAIClient`

**Responsibility**: Implements `LLMClient` by calling OpenAI Chat Completions over HTTP.

**Key fields**
- `API_URL = https://api.openai.com/v1/chat/completions`
- `apiKey`, `model`, `systemPrompt`
- `HttpClient httpClient`
- `ObjectMapper objectMapper`

**Methods**
- `public OpenAIClient()`
  - Loads `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_SYSTEM_PROMPT` from `.env` (if present)
  - Falls back to environment variables and system properties:
    - model: `openai.model` → `OPENAI_MODEL` → default `gpt-5.2`
    - system prompt: `openai.systemPrompt` → `OPENAI_SYSTEM_PROMPT` → built-in default instructions
  - Builds `HttpClient` and Jackson mapper.

- `public String sendPrompt(String prompt)`
  - Returns `null` if API key missing.
  - Builds request JSON (`buildRequestBody`)
  - Sends POST request to `API_URL`
  - On HTTP 200:
    - parses `choices[0].message.content`
    - returns trimmed content
  - Otherwise logs error and returns `null`.

- `private String buildRequestBody(String prompt)`
  - Creates JSON payload:
    - `model`, `temperature`, `max_tokens`
    - `messages`: system prompt + user prompt
  - Serializes via `ObjectMapper`.

- `private static String defaultSystemPrompt()`
  - The built-in “how to process the request” instructions:
    - output exactly one selector line
    - CSS preferred, XPath must be prefixed with `xpath=`
    - no prose/markdown
    - avoid brittle selectors

---

### `com.automation.ai.AIHealingEngine`

**Responsibility**: High-level healing orchestration:

- optionally consult cache
- clean + redact HTML
- build prompt
- call LLM
- cache successful heals
- write audit logs

**Key fields**
- `Logger AI_HEALING_LOGGER = LogManager.getLogger("AIHealingAudit")`
- `LLMClient llmClient` (nullable if missing API key)
- `HealingCache healingCache`

**Methods**
- Constructors:
  - `AIHealingEngine()`: uses `new OpenAIClient()` + `defaultCache()`
  - `AIHealingEngine(LLMClient llmClient)`: uses default cache
  - `AIHealingEngine(LLMClient llmClient, HealingCache healingCache)`:
    - sets `client = llmClient`
    - checks for `OPENAI_API_KEY` presence and logs info if missing
    - assigns `this.llmClient` and `this.healingCache`

- `public String heal(String failedSelector, String htmlContent)`
  - Convenience overload; calls `heal(..., true)`.

- `public String heal(String failedSelector, String htmlContent, boolean allowCache)`
  - If invalid selector, returns `null`.
  - If cache allowed and hit exists, returns it immediately.
  - Else:
    - cleans HTML (`HtmlCleaner`)
    - redacts HTML (`HtmlRedactor`)
    - builds prompt (`buildPrompt`)
    - logs audit entries (failed selector, prompt)
    - calls LLM (`callLLM`)
    - on success: caches healed selector and returns it

- `private String buildPrompt(String failedSelector, String cleanedHtml)`
  - Creates a string prompt and truncates HTML to 10,000 characters.

- `private String callLLM(String prompt)`
  - Calls `llmClient.sendPrompt(prompt)` if available
  - Removes markdown code fences (defensive cleanup)
  - Returns selector string or `null`

- `private static HealingCache defaultCache()`
  - Chooses cache path:
    - `-Dhealing.cache.path=...` if provided
    - else `healing-cache.json` in working directory

---

### `com.automation.tests.LoginTest` (TestNG example)

**Responsibility**: Demonstrates framework usage.

**Fields**
- `SeleniumFactory seleniumFactory`
- `LoginPage loginPage`

**Methods**
- `@BeforeMethod setUp()`
  - Gets singleton driver factory
  - Creates `LoginPage` (which triggers element wiring)

- `@Test testLoginWithJsonData()`
  - Loads user from `user.json`
  - Navigates to demo login page
  - Uses `LoginPage` methods (internally uses smart actions through `SmartElement`)

- `@Test testLoginWithDirectSmartActions()`
  - Shows direct use of `smartFill` / `smartClick` on the page

- `@AfterSuite tearDown()`
  - Closes the shared driver

---

### Logging configuration (`src/main/resources/log4j2.xml`)

**Appenders**
- Console
- Rolling file: `logs/application.log`
- Rolling file: `logs/ai-healing-audit.log`

**Loggers**
- `Logger name="AIHealingAudit"` sends to AI audit + console (non-additive)
- Root logger sends to console + general file

---

### TestNG suite config (`testng.xml`)

- Runs the `com.automation.tests.LoginTest` class.
- Suite has `parallel="methods"` but `thread-count="1"`, effectively serial execution.

---

### How to extend this framework (practical guide)

#### Add a new Page Object

1. Create a class extending `BasePage`.
2. Declare elements as `private SmartElement ...;` and annotate with `@SeleniumSelector("...")`.
3. Add business methods that call `element.click()` / `element.fill()`.

Because elements are injected in `BasePage` constructor, fields are ready after `super(driver)`.

#### Add a new smart action

Add a method to `BasePage` (example patterns already exist):
- implement explicit wait
- catch `TimeoutException`
- attempt healing + validation

#### Use a different LLM provider

1. Implement `LLMClient` (e.g., `AnthropicClient implements LLMClient`)
2. Construct `AIHealingEngine(new AnthropicClient(), cache)` and inject into pages (requires small refactor: allow `BasePage` to accept an engine or client).

---

### Common troubleshooting

- **Driver fails to start**
  - Ensure browsers are installed and Selenium Manager can resolve drivers.
  - Check `browser.type` in `config.properties`.

- **AI healing never triggers**
  - It only triggers on `TimeoutException` inside `smartClick` / `smartFill`.
  - Ensure actions go through `SmartElement` that was initialized with a `BasePage` reference (i.e., page extends `BasePage` and uses `@SeleniumSelector`).

- **OpenAI calls fail**
  - Ensure `OPENAI_API_KEY` is set.
  - Check logs for HTTP status + response.

- **Healed selector is rejected**
  - `BasePage` validation requires exactly one element match and visibility/clickability.
  - This is intentional to avoid bad heals; tune validation if needed.

