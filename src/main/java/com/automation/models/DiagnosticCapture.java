package com.automation.models;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured snapshot of a locator failure for AI analysis.
 * Captures everything needed for the LLM to suggest replacement selectors.
 */
@Data
@Builder
public class DiagnosticCapture {

    /** Timestamp of the failure. */
    private LocalDateTime timestamp;

    /** Fully qualified test method name (e.g., com.automation.tests.LoginTest#testLogin). */
    private String testMethodName;

    /** The selector string that failed (e.g., "input[name='username']" or "xpath=//div[@id='x']"). */
    private String failedSelector;

    /** The exception type that triggered the capture. */
    private String exceptionType;

    /** The exception message. */
    private String exceptionMessage;

    /** Full page source HTML at the moment of failure. */
    private String fullPageHtml;

    /** Simplified DOM tree (scripts, styles, SVGs, meta removed). */
    private String simplifiedDom;

    /** Focused HTML snippet near the failed element (outerHTML of nearby/parent elements). */
    private String focusHtml;

    /** Computed CSS styles of the target element (from a previous successful state or nearby elements). */
    private String computedCssStyles;

    /** Base64-encoded PNG screenshot of the entire page. */
    private String screenshotBase64Png;

    /** File path to the saved screenshot on disk. */
    private String screenshotFilePath;

    /** AI-suggested replacement selectors (ordered by confidence). */
    private List<String> suggestedSelectors;

    /** The selector that ultimately worked (null if healing failed). */
    private String healedSelector;

    /** Whether the healing was successful. */
    private boolean healed;

    /** Page URL at time of failure. */
    private String pageUrl;

    /** Page title at time of failure. */
    private String pageTitle;
}
