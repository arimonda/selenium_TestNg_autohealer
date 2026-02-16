package com.automation.listeners;

import com.automation.models.HealingResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe shared context that stores healing results across the entire test run.
 * Used by the listener to collect data and by the reporter to generate the report.
 */
@Slf4j
public final class HealingContext {
    private HealingContext() {}

    private static final CopyOnWriteArrayList<HealingResult> RESULTS = new CopyOnWriteArrayList<>();

    /** Record a healing result. */
    public static void record(HealingResult result) {
        if (result != null) {
            RESULTS.add(result);
            log.debug("[HealingContext] Recorded: {} -> {}", result.getOriginalSelector(), result.getOutcome());
        }
    }

    /** Get an unmodifiable snapshot of all recorded results. */
    public static List<HealingResult> getResults() {
        return Collections.unmodifiableList(RESULTS);
    }

    /** Number of tests that were healed. */
    public static long healedCount() {
        return RESULTS.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.HEALED
                          || r.getOutcome() == HealingResult.HealingOutcome.CACHE_HIT)
                .count();
    }

    /** Number of tests where healing failed. */
    public static long failedCount() {
        return RESULTS.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.FAILED)
                .count();
    }

    /** Clear all recorded results (for test isolation). */
    public static void clear() {
        RESULTS.clear();
    }
}
