package com.automation.base;

import com.automation.ai.AIHealingEngine;
import com.automation.config.HealingConfig;
import com.automation.listeners.HealingContext;
import com.automation.models.HealingResult;
import com.automation.pages.ElementInitializer;
import com.automation.utils.SelectorCodeUpdater;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base page with AI Self-Healing capabilities.
 * <p>
 * Provides {@code smartClick()} and {@code smartFill()} that:
 * <ol>
 *   <li>Attempt the action with the original selector.</li>
 *   <li>On {@link NoSuchElementException}, {@link TimeoutException}, or {@link StaleElementReferenceException},
 *       enter "Heal Mode" and consult the AI engine for up to {@code retry.count} alternative locators.</li>
 *   <li>Sequentially validate each suggested locator.</li>
 *   <li>On success, persist the healed selector (in-memory + source code if configured).</li>
 *   <li>Record a {@link HealingResult} in {@link HealingContext} for reporting.</li>
 * </ol>
 */
@Slf4j
public abstract class BasePage {
    protected WebDriver driver;
    private final AIHealingEngine healingEngine;
    private final HealingConfig config;
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);
    private static final Duration HEAL_VALIDATION_WAIT = Duration.ofSeconds(3);

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.config = HealingConfig.getInstance();
        this.healingEngine = new AIHealingEngine();
        ElementInitializer.initElements(driver, this);
    }

    // ================================================================== public API

    public void smartClick(String selector) {
        smartClickInternal(selector, null);
    }

    public void smartClick(SmartElement element) {
        if (element == null) return;
        smartClickInternal(element.getSelector(), element);
    }

    public void smartFill(String selector, String value) {
        smartFillInternal(selector, value, null);
    }

    public void smartFill(SmartElement element, String value) {
        if (element == null) return;
        smartFillInternal(element.getSelector(), value, element);
    }

    public void navigateTo(String url) {
        log.info("Navigating to: {}", url);
        driver.get(url);
    }

    // ================================================================== smart click

    private void smartClickInternal(String selector, SmartElement sourceElement) {
        String currentSelector = selector;

        // --- First attempt with original selector ---
        try {
            log.info("Attempting to click element with selector: {}", currentSelector);
            By by = toBy(currentSelector);
            WebElement element = waitForClickable(by);
            element.click();
            log.info("Successfully clicked element with selector: {}", currentSelector);
            return;
        } catch (NoSuchElementException | TimeoutException | StaleElementReferenceException e) {
            if (!config.isHealingEnabled()) throw e;
            log.warn("Click failed with selector: {}. Entering Heal Mode...", currentSelector);
        }

        // --- Heal Mode: get multiple suggestions and iterate ---
        long start = System.currentTimeMillis();
        String focusHtml = buildFocusHtml(currentSelector);
        String screenshotBase64 = captureScreenshotBase64AndSave("click", currentSelector);

        List<String> suggestions = healingEngine.healMultiple(
                currentSelector, driver.getPageSource(), true, focusHtml, screenshotBase64);

        String healedSelector = null;
        int winningIdx = -1;

        for (int i = 0; i < suggestions.size(); i++) {
            String candidate = suggestions.get(i);
            log.info("[HEAL-MODE] Trying suggestion {}/{}: {}", i + 1, suggestions.size(), candidate);
            try {
                By by = toBy(candidate);
                WebElement el = new WebDriverWait(driver, HEAL_VALIDATION_WAIT)
                        .until(ExpectedConditions.elementToBeClickable(by));
                el.click();
                healedSelector = candidate;
                winningIdx = i + 1;
                log.info("[HEAL-MODE] Suggestion #{} worked: {}", winningIdx, candidate);
                break;
            } catch (Exception ex) {
                log.debug("[HEAL-MODE] Suggestion #{} failed: {}", i + 1, ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;

        // --- Record result ---
        HealingResult.HealingOutcome outcome = healedSelector != null
                ? HealingResult.HealingOutcome.HEALED
                : HealingResult.HealingOutcome.FAILED;

        HealingContext.record(HealingResult.builder()
                .testName(inferTestName())
                .testClass(this.getClass().getSimpleName())
                .originalSelector(selector)
                .healedSelector(healedSelector)
                .suggestedSelectors(suggestions)
                .winningIndex(winningIdx)
                .outcome(outcome)
                .aiProvider(config.getAiProvider())
                .healingDurationMs(duration)
                .timestamp(LocalDateTime.now())
                .codeUpdated(false)
                .build());

        if (healedSelector == null) {
            log.error("[HEAL-MODE] All {} suggestions failed for selector: {}", suggestions.size(), selector);
            throw new NoSuchElementException(
                    "AI healing exhausted all suggestions for selector: " + selector);
        }

        // --- Persist healed selector ---
        persistHealedSelectorIfPossible(sourceElement, selector, healedSelector);
    }

    // ================================================================== smart fill

    private void smartFillInternal(String selector, String value, SmartElement sourceElement) {
        String currentSelector = selector;

        // --- First attempt with original selector ---
        try {
            log.info("Attempting to fill element with selector: {} with value: {}", currentSelector, value);
            By by = toBy(currentSelector);
            WebElement element = waitForVisible(by);
            element.clear();
            element.sendKeys(value);
            log.info("Successfully filled element with selector: {}", currentSelector);
            return;
        } catch (NoSuchElementException | TimeoutException | StaleElementReferenceException e) {
            if (!config.isHealingEnabled()) throw e;
            log.warn("Fill failed with selector: {}. Entering Heal Mode...", currentSelector);
        }

        // --- Heal Mode ---
        long start = System.currentTimeMillis();
        String focusHtml = buildFocusHtml(currentSelector);
        String screenshotBase64 = captureScreenshotBase64AndSave("fill", currentSelector);

        List<String> suggestions = healingEngine.healMultiple(
                currentSelector, driver.getPageSource(), true, focusHtml, screenshotBase64);

        String healedSelector = null;
        int winningIdx = -1;

        for (int i = 0; i < suggestions.size(); i++) {
            String candidate = suggestions.get(i);
            log.info("[HEAL-MODE] Trying suggestion {}/{}: {}", i + 1, suggestions.size(), candidate);
            try {
                By by = toBy(candidate);
                WebElement el = new WebDriverWait(driver, HEAL_VALIDATION_WAIT)
                        .until(ExpectedConditions.visibilityOfElementLocated(by));
                if (el.isEnabled()) {
                    el.clear();
                    el.sendKeys(value);
                    healedSelector = candidate;
                    winningIdx = i + 1;
                    log.info("[HEAL-MODE] Suggestion #{} worked: {}", winningIdx, candidate);
                    break;
                }
            } catch (Exception ex) {
                log.debug("[HEAL-MODE] Suggestion #{} failed: {}", i + 1, ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;

        HealingResult.HealingOutcome outcome = healedSelector != null
                ? HealingResult.HealingOutcome.HEALED
                : HealingResult.HealingOutcome.FAILED;

        HealingContext.record(HealingResult.builder()
                .testName(inferTestName())
                .testClass(this.getClass().getSimpleName())
                .originalSelector(selector)
                .healedSelector(healedSelector)
                .suggestedSelectors(suggestions)
                .winningIndex(winningIdx)
                .outcome(outcome)
                .aiProvider(config.getAiProvider())
                .healingDurationMs(duration)
                .timestamp(LocalDateTime.now())
                .codeUpdated(false)
                .build());

        if (healedSelector == null) {
            log.error("[HEAL-MODE] All {} suggestions failed for selector: {}", suggestions.size(), selector);
            throw new NoSuchElementException(
                    "AI healing exhausted all suggestions for selector: " + selector);
        }

        persistHealedSelectorIfPossible(sourceElement, selector, healedSelector);
    }

    // ================================================================== persistence

    private void persistHealedSelectorIfPossible(SmartElement element, String oldSelector, String newSelector) {
        if (element == null) return;

        // Update in-memory
        element.updateSelector(newSelector);

        if (!config.isAutoUpdateCode()) return;
        if (element.getOwnerPageClass() == null || element.getFieldName() == null) return;

        boolean updated = SelectorCodeUpdater.updateAnnotatedSelector(
                element.getOwnerPageClass(),
                element.getFieldName(),
                oldSelector,
                newSelector
        );
        if (updated) {
            log.warn("[AI-HEAL] Source code auto-updated for {}#{}: {} -> {}",
                    element.getOwnerPageClass().getSimpleName(), element.getFieldName(),
                    oldSelector, newSelector);
        }
    }

    // ================================================================== waits

    protected WebElement waitForVisible(By by) {
        return new WebDriverWait(driver, DEFAULT_WAIT)
                .until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected WebElement waitForClickable(By by) {
        return new WebDriverWait(driver, DEFAULT_WAIT)
                .until(ExpectedConditions.elementToBeClickable(by));
    }

    // ================================================================== focus HTML

    private String buildFocusHtml(String failedSelector) {
        try {
            List<String> chunks = new ArrayList<>();

            // 1) Direct match
            try {
                By by = toBy(failedSelector);
                List<WebElement> directMatches = driver.findElements(by);
                for (int i = 0; i < Math.min(3, directMatches.size()); i++) {
                    String html = outerHtmlWithParents(directMatches.get(i), 2);
                    if (html != null && !html.isBlank()) chunks.add(html);
                }
            } catch (Exception ignored) {}

            // 2) Heuristic match
            if (chunks.isEmpty()) {
                for (String candidate : deriveCandidateSelectors(failedSelector)) {
                    try {
                        List<WebElement> matches = driver.findElements(By.cssSelector(candidate));
                        for (int i = 0; i < Math.min(3, matches.size()); i++) {
                            String html = outerHtmlWithParents(matches.get(i), 2);
                            if (html != null && !html.isBlank())
                                chunks.add("<!-- matched via: " + candidate + " -->\n" + html);
                        }
                    } catch (Exception ignored) {}
                    if (chunks.size() >= 3) break;
                }
            }

            // 3) Computed CSS styles (if enabled)
            if (config.isDomCaptureCss() && !chunks.isEmpty()) {
                try {
                    chunks.add(captureComputedStyles(failedSelector));
                } catch (Exception ignored) {}
            }

            if (chunks.isEmpty()) return null;
            String joined = String.join("\n\n<!-- -------- -->\n\n", chunks);
            return joined.substring(0, Math.min(joined.length(), 20_000));
        } catch (Exception e) {
            return null;
        }
    }

    private String captureComputedStyles(String selector) {
        if (!(driver instanceof JavascriptExecutor js)) return null;
        try {
            By by = toBy(selector);
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            Object result = js.executeScript(
                    "var el = arguments[0]; var cs = window.getComputedStyle(el);" +
                    "var props = ['display','visibility','opacity','position','width','height'," +
                    "'top','left','z-index','overflow','pointer-events'];" +
                    "var out = {}; props.forEach(function(p){out[p]=cs.getPropertyValue(p);}); return JSON.stringify(out);",
                    els.get(0)
            );
            return result != null ? "<!-- computed-css -->\n" + result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String outerHtmlWithParents(WebElement element, int parentLevels) {
        if (!(driver instanceof JavascriptExecutor js)) return null;
        try {
            return (String) js.executeScript(
                    "var el = arguments[0]; if (!el) return null;" +
                    "var out = []; var cur = el; out.push(cur.outerHTML);" +
                    "for (var i = 0; i < arguments[1]; i++) {" +
                    "  if (!cur.parentElement) break; cur = cur.parentElement; out.push(cur.outerHTML);}" +
                    "return out.join('\\n\\n<!-- parent -->\\n\\n');",
                    element, parentLevels);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> deriveCandidateSelectors(String selector) {
        List<String> candidates = new ArrayList<>();
        if (selector == null) return candidates;
        String s = selector.trim();
        if (s.startsWith("xpath=")) return candidates;

        Matcher idMatcher = Pattern.compile("#([A-Za-z0-9_-]+)").matcher(s);
        if (idMatcher.find()) {
            String id = idMatcher.group(1);
            candidates.add("#" + id);
            candidates.add("*[id='" + cssEscape(id) + "']");
        }

        Matcher attrMatcher = Pattern.compile("\\[\\s*([A-Za-z0-9_:\\-]+)\\s*=\\s*(['\"])(.*?)\\2\\s*]").matcher(s);
        while (attrMatcher.find()) {
            String attr = attrMatcher.group(1);
            String val = attrMatcher.group(3);
            String cand = "*[" + attr + "='" + cssEscape(val) + "']";
            String normalizedAttr = attr.toLowerCase();
            if (normalizedAttr.startsWith("data-") || normalizedAttr.equals("name")
                    || normalizedAttr.equals("aria-label") || normalizedAttr.equals("role")
                    || normalizedAttr.equals("placeholder") || normalizedAttr.equals("type")
                    || normalizedAttr.equals("title") || normalizedAttr.equals("for")) {
                candidates.add(0, cand);
            } else {
                candidates.add(cand);
            }
        }

        List<String> uniq = new ArrayList<>();
        for (String c : candidates) {
            if (c != null && !c.isBlank() && !uniq.contains(c)) uniq.add(c);
        }
        return uniq;
    }

    private String cssEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    // ================================================================== screenshot

    private String captureScreenshotBase64AndSave(String action, String selector) {
        if (!config.isScreenshotEnabled()) return null;
        try {
            if (!(driver instanceof TakesScreenshot)) return null;
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            if (bytes == null || bytes.length == 0) return null;

            Path dir = Paths.get(config.getScreenshotDirectory());
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safeAction = safeFileComponent(action);
            String safeSel = safeFileComponent(selector);
            if (safeSel.length() > 60) safeSel = safeSel.substring(0, 60);
            Path file = dir.resolve(ts + "_" + safeAction + "_" + safeSel + ".png");
            Files.write(file, bytes);
            log.warn("[AI-HEAL] Screenshot captured: {}", file.toAbsolutePath());

            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeFileComponent(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    // ================================================================== helpers

    protected By toBy(String selector) {
        if (selector == null) throw new IllegalArgumentException("Selector cannot be null");
        String trimmed = selector.trim();
        if (trimmed.startsWith("xpath=")) return By.xpath(trimmed.substring("xpath=".length()));
        if (trimmed.startsWith("//") || trimmed.startsWith("(//")) return By.xpath(trimmed);
        return By.cssSelector(trimmed);
    }

    private String inferTestName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().contains(".tests.") || frame.getClassName().endsWith("Test")) {
                return frame.getMethodName();
            }
        }
        return "unknown";
    }
}
