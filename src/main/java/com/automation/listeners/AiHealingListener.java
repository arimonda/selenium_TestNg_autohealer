package com.automation.listeners;

import com.automation.ai.AIHealingEngine;
import com.automation.config.HealingConfig;
import com.automation.models.DiagnosticCapture;
import com.automation.models.HealingResult;
import com.automation.utils.HtmlCleaner;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * TestNG {@link ITestListener} that intercepts locator-based test failures and triggers
 * the AI Self-Healing diagnostic capture pipeline.
 * <p>
 * When a test fails with {@link NoSuchElementException} or {@link TimeoutException},
 * this listener:
 * <ol>
 *   <li>Captures a {@link DiagnosticCapture} (page source, screenshot, simplified DOM)</li>
 *   <li>Consults the AI engine for replacement locators</li>
 *   <li>Records a {@link HealingResult} in the shared {@link HealingContext}</li>
 * </ol>
 * <p>
 * <b>Note:</b> This listener acts as a diagnostic safety net. The primary healing mechanism
 * lives in {@code BasePage.smartClick/smartFill} which heals inline during test execution.
 * This listener captures data for reporting and handles edge cases that bypass BasePage.
 */
@Slf4j
public class AiHealingListener implements ITestListener {

    private static final Logger AI_AUDIT = LogManager.getLogger("AIHealingAudit");
    private final AIHealingEngine healingEngine = new AIHealingEngine();

    @Override
    public void onTestStart(ITestResult result) {
        log.debug("[AiHealingListener] Test started: {}", getTestName(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.debug("[AiHealingListener] Test passed: {}", getTestName(result));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        HealingConfig config = HealingConfig.getInstance();
        if (!config.isHealingEnabled()) {
            return;
        }

        Throwable throwable = result.getThrowable();
        if (!isLocatorFailure(throwable)) {
            log.debug("[AiHealingListener] Non-locator failure in {}. Skipping healing.",
                    getTestName(result));
            return;
        }

        log.warn("[AiHealingListener] Locator failure detected in {}. Running diagnostic capture...",
                getTestName(result));
        AI_AUDIT.warn("[AiHealingListener] Locator failure in: {}", getTestName(result));

        try {
            // Attempt to retrieve WebDriver from the test instance
            WebDriver driver = extractDriver(result);
            if (driver == null) {
                log.warn("[AiHealingListener] Could not obtain WebDriver. Diagnostic capture skipped.");
                recordSkipped(result, "WebDriver not accessible");
                return;
            }

            String failedSelector = extractFailedSelector(throwable);
            DiagnosticCapture capture = buildDiagnosticCapture(result, driver, failedSelector, throwable);

            // Consult AI
            long start = System.currentTimeMillis();
            List<String> suggestions = healingEngine.healMultiple(
                    failedSelector,
                    capture.getFullPageHtml(),
                    true,
                    capture.getFocusHtml(),
                    capture.getScreenshotBase64Png()
            );
            long duration = System.currentTimeMillis() - start;

            capture.setSuggestedSelectors(suggestions);

            // Validate suggestions against the live page
            String winningSelector = null;
            int winningIdx = -1;
            for (int i = 0; i < suggestions.size(); i++) {
                if (validateSelector(driver, suggestions.get(i))) {
                    winningSelector = suggestions.get(i);
                    winningIdx = i + 1;
                    break;
                }
            }

            capture.setHealedSelector(winningSelector);
            capture.setHealed(winningSelector != null);

            HealingResult healingResult = HealingResult.builder()
                    .testName(result.getMethod().getMethodName())
                    .testClass(result.getTestClass().getName())
                    .originalSelector(failedSelector)
                    .healedSelector(winningSelector)
                    .suggestedSelectors(suggestions)
                    .winningIndex(winningIdx)
                    .outcome(winningSelector != null
                            ? HealingResult.HealingOutcome.HEALED
                            : HealingResult.HealingOutcome.FAILED)
                    .aiProvider(config.getAiProvider())
                    .healingDurationMs(duration)
                    .timestamp(LocalDateTime.now())
                    .diagnosticCapture(capture)
                    .build();

            HealingContext.record(healingResult);

            if (winningSelector != null) {
                AI_AUDIT.info("[AiHealingListener] Healing successful: {} -> {} (suggestion #{})",
                        failedSelector, winningSelector, winningIdx);
            } else {
                AI_AUDIT.warn("[AiHealingListener] Healing failed for selector: {}", failedSelector);
            }

        } catch (Exception e) {
            log.error("[AiHealingListener] Error during diagnostic capture", e);
            AI_AUDIT.error("[AiHealingListener] Diagnostic capture error", e);
            recordSkipped(result, e.getMessage());
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.debug("[AiHealingListener] Test skipped: {}", getTestName(result));
    }

    @Override
    public void onStart(ITestContext context) {
        log.info("[AiHealingListener] Suite started: {}", context.getName());
        AI_AUDIT.info("=== AI Healing Suite Start: {} ===", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        long healed = HealingContext.healedCount();
        long failed = HealingContext.failedCount();
        log.info("[AiHealingListener] Suite finished: {}. Healed: {}, Failed: {}", context.getName(), healed, failed);
        AI_AUDIT.info("=== AI Healing Suite End: {} | Healed: {} | Failed: {} ===",
                context.getName(), healed, failed);
    }

    // ------------------------------------------------------------------ helpers

    /** Checks whether the exception is a locator-related failure. */
    static boolean isLocatorFailure(Throwable t) {
        if (t == null) return false;
        if (t instanceof NoSuchElementException) return true;
        if (t instanceof TimeoutException) return true;
        if (t instanceof StaleElementReferenceException) return true;
        if (t instanceof InvalidSelectorException) return true;
        // Check cause chain
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return isLocatorFailure(cause);
        }
        // Check message for common patterns
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            return lower.contains("no such element")
                    || lower.contains("unable to locate")
                    || lower.contains("element not found")
                    || lower.contains("timeout")
                    || lower.contains("stale element");
        }
        return false;
    }

    /** Best-effort extraction of the failing selector from the exception message. */
    static String extractFailedSelector(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (msg == null) return "unknown";

        // NoSuchElementException: "no such element: Unable to locate element: {"method":"css selector","selector":"..."}"
        int selectorIdx = msg.indexOf("\"selector\":\"");
        if (selectorIdx >= 0) {
            int start = selectorIdx + "\"selector\":\"".length();
            int end = msg.indexOf("\"", start);
            if (end > start) {
                return msg.substring(start, end);
            }
        }

        // TimeoutException often wraps the selector in "Expected condition failed: ... By.cssSelector: ..."
        int byIdx = msg.indexOf("By.cssSelector: ");
        if (byIdx >= 0) {
            int start = byIdx + "By.cssSelector: ".length();
            int end = msg.indexOf("\n", start);
            if (end < 0) end = msg.length();
            return msg.substring(start, end).trim();
        }

        byIdx = msg.indexOf("By.xpath: ");
        if (byIdx >= 0) {
            int start = byIdx + "By.xpath: ".length();
            int end = msg.indexOf("\n", start);
            if (end < 0) end = msg.length();
            return "xpath=" + msg.substring(start, end).trim();
        }

        return "unknown";
    }

    private DiagnosticCapture buildDiagnosticCapture(ITestResult result, WebDriver driver,
                                                     String failedSelector, Throwable throwable) {
        String pageSource = safeGetPageSource(driver);
        String simplifiedDom = HtmlCleaner.cleanHtml(pageSource);
        String screenshotBase64 = captureScreenshot(driver, result.getMethod().getMethodName());

        return DiagnosticCapture.builder()
                .timestamp(LocalDateTime.now())
                .testMethodName(getTestName(result))
                .failedSelector(failedSelector)
                .exceptionType(throwable.getClass().getSimpleName())
                .exceptionMessage(throwable.getMessage())
                .fullPageHtml(pageSource)
                .simplifiedDom(simplifiedDom)
                .screenshotBase64Png(screenshotBase64)
                .pageUrl(safeGetCurrentUrl(driver))
                .pageTitle(safeGetTitle(driver))
                .healed(false)
                .build();
    }

    private String captureScreenshot(WebDriver driver, String testName) {
        HealingConfig config = HealingConfig.getInstance();
        if (!config.isScreenshotEnabled()) return null;

        try {
            if (!(driver instanceof TakesScreenshot)) return null;
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            if (bytes == null || bytes.length == 0) return null;

            Path dir = Paths.get(config.getScreenshotDirectory());
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safeName = testName.replaceAll("[^A-Za-z0-9._-]+", "_");
            Path file = dir.resolve(ts + "_listener_" + safeName + ".png");
            Files.write(file, bytes);
            log.info("[AiHealingListener] Screenshot saved: {}", file.toAbsolutePath());

            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[AiHealingListener] Failed to capture screenshot", e);
            return null;
        }
    }

    private boolean validateSelector(WebDriver driver, String selector) {
        try {
            By by = toBy(selector);
            List<WebElement> matches = driver.findElements(by);
            return matches.size() == 1 && matches.get(0).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private By toBy(String selector) {
        String trimmed = selector.trim();
        if (trimmed.startsWith("xpath=")) {
            return By.xpath(trimmed.substring("xpath=".length()));
        }
        return By.cssSelector(trimmed);
    }

    private WebDriver extractDriver(ITestResult result) {
        Object instance = result.getInstance();
        if (instance == null) return null;

        // Look for a field named "driver" or a SeleniumFactory field
        try {
            java.lang.reflect.Field[] fields = instance.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof WebDriver) {
                    return (WebDriver) value;
                }
                // Check for SeleniumFactory
                if (value != null && value.getClass().getSimpleName().equals("SeleniumFactory")) {
                    try {
                        java.lang.reflect.Method getDriver = value.getClass().getMethod("getDriver");
                        Object driver = getDriver.invoke(value);
                        if (driver instanceof WebDriver) {
                            return (WebDriver) driver;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("[AiHealingListener] Could not extract WebDriver via reflection", e);
        }
        return null;
    }

    private void recordSkipped(ITestResult result, String reason) {
        HealingContext.record(HealingResult.builder()
                .testName(result.getMethod().getMethodName())
                .testClass(result.getTestClass().getName())
                .outcome(HealingResult.HealingOutcome.SKIPPED)
                .suggestedSelectors(Collections.emptyList())
                .winningIndex(-1)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private String getTestName(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    }

    private String safeGetPageSource(WebDriver driver) {
        try { return driver.getPageSource(); }
        catch (Exception e) { return ""; }
    }

    private String safeGetCurrentUrl(WebDriver driver) {
        try { return driver.getCurrentUrl(); }
        catch (Exception e) { return ""; }
    }

    private String safeGetTitle(WebDriver driver) {
        try { return driver.getTitle(); }
        catch (Exception e) { return ""; }
    }
}
