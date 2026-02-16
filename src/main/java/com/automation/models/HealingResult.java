package com.automation.models;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records the outcome of a healing attempt.
 * Stored per-test and aggregated in the healing report.
 */
@Data
@Builder
public class HealingResult {

    /** Test method that was healed. */
    private String testName;

    /** Class containing the test method. */
    private String testClass;

    /** Original failing selector. */
    private String originalSelector;

    /** Selector that worked after healing (null if all failed). */
    private String healedSelector;

    /** All selectors suggested by the AI. */
    private List<String> suggestedSelectors;

    /** Index (1-based) of the suggestion that worked, or -1 if none worked. */
    private int winningIndex;

    /** Outcome: HEALED, FAILED, SKIPPED. */
    private HealingOutcome outcome;

    /** AI provider used (GEMINI, OPENAI, CLAUDE). */
    private String aiProvider;

    /** Duration of the healing attempt in milliseconds. */
    private long healingDurationMs;

    /** Timestamp. */
    private LocalDateTime timestamp;

    /** Whether source code was auto-updated. */
    private boolean codeUpdated;

    /** Optional diagnostic capture reference. */
    private DiagnosticCapture diagnosticCapture;

    public enum HealingOutcome {
        HEALED,
        FAILED,
        SKIPPED,
        CACHE_HIT
    }
}
