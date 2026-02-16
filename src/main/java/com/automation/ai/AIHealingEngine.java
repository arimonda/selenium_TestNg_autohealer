package com.automation.ai;

import com.automation.config.HealingConfig;
import com.automation.utils.HtmlCleaner;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core AI Healing Engine.
 * <p>
 * Accepts a failed selector + page HTML (and optionally a screenshot) and
 * consults an LLM to produce <b>multiple</b> alternative locators.
 * <p>
 * Supports Gemini, OpenAI, and Claude providers through the {@link LLMClient} interface.
 */
@Slf4j
public class AIHealingEngine {
    private static final Logger AI_HEALING_LOGGER = LogManager.getLogger("AIHealingAudit");
    private final LLMClient llmClient;
    private final HealingCache healingCache;
    private final HealingConfig config;

    public AIHealingEngine() {
        this(defaultClient(), defaultCache());
    }

    public AIHealingEngine(LLMClient llmClient) {
        this(llmClient, defaultCache());
    }

    public AIHealingEngine(LLMClient llmClient, HealingCache healingCache) {
        this.config = HealingConfig.getInstance();

        LLMClient client = null;
        try {
            client = llmClient;
        } catch (Exception e) {
            log.warn("Failed to initialize LLM client. AI healing will use fallback.", e);
        }
        this.llmClient = client;
        this.healingCache = healingCache;
    }

    // ------------------------------------------------------------------ single selector (backward-compatible)

    public String heal(String failedSelector, String htmlContent) {
        return heal(failedSelector, htmlContent, true);
    }

    public String heal(String failedSelector, String htmlContent, boolean allowCache) {
        return heal(failedSelector, htmlContent, allowCache, null);
    }

    public String heal(String failedSelector, String htmlContent, boolean allowCache, String focusHtml) {
        return heal(failedSelector, htmlContent, allowCache, focusHtml, null);
    }

    /**
     * Single-selector healing (backward-compatible).
     * Returns the first valid suggestion or {@code null}.
     */
    public String heal(String failedSelector, String htmlContent, boolean allowCache,
                       String focusHtml, String screenshotBase64Png) {
        List<String> suggestions = healMultiple(failedSelector, htmlContent, allowCache, focusHtml, screenshotBase64Png);
        return suggestions.isEmpty() ? null : suggestions.get(0);
    }

    // ------------------------------------------------------------------ multi-locator healing

    /**
     * Returns up to {@code retryCount} alternative locators for a failed selector.
     * <p>
     * Order of precedence:
     * <ol>
     *   <li>Cache hit (if allowCache)</li>
     *   <li>LLM suggestions (parsed from numbered list)</li>
     * </ol>
     */
    public List<String> healMultiple(String failedSelector, String htmlContent,
                                     boolean allowCache, String focusHtml,
                                     String screenshotBase64Png) {
        if (failedSelector == null || failedSelector.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (!config.isHealingEnabled()) {
            log.info("[AI-HEAL] Healing is disabled via configuration.");
            return Collections.emptyList();
        }

        // 1) Check cache first
        if (allowCache && healingCache != null) {
            Optional<String> cached = healingCache.get(failedSelector);
            if (cached.isPresent() && !cached.get().trim().isEmpty()) {
                String cachedSelector = cached.get().trim();
                log.info("[AI-HEAL] Cache hit for selector: {} -> {}", failedSelector, cachedSelector);
                AI_HEALING_LOGGER.info("[AI-HEAL] Cache hit: {} -> {}", failedSelector, cachedSelector);
                return List.of(cachedSelector);
            }
        }

        // 2) Build prompt and consult LLM
        String cleanedHtml = HtmlCleaner.cleanHtml(htmlContent);
        String redactedHtml = HtmlRedactor.redact(cleanedHtml);
        String prompt = buildPrompt(failedSelector, redactedHtml, focusHtml, screenshotBase64Png);

        log.info("[AI-HEAL] Attempting to heal selector: {}", failedSelector);
        AI_HEALING_LOGGER.info("[AI-HEAL] Failed selector: {}", failedSelector);
        AI_HEALING_LOGGER.info("[AI-HEAL] Screenshot attached: {}",
                screenshotBase64Png != null && !screenshotBase64Png.isBlank());
        AI_HEALING_LOGGER.info("[AI-HEAL] Prompt length: {} chars", prompt.length());
        AI_HEALING_LOGGER.info("[AI-HEAL] Prompt preview (first 2000 chars): {}",
                prompt.substring(0, Math.min(prompt.length(), 2000)));

        String rawResponse = callLLM(prompt, screenshotBase64Png);
        List<String> suggestions = parseMultipleSelectors(rawResponse);

        if (!suggestions.isEmpty()) {
            log.info("[AI-HEAL] Received {} alternative locators for: {}", suggestions.size(), failedSelector);
            for (int i = 0; i < suggestions.size(); i++) {
                AI_HEALING_LOGGER.info("[AI-HEAL] Suggestion {}: {}", i + 1, suggestions.get(i));
            }
            // Cache the first suggestion
            String first = suggestions.get(0);
            if (healingCache != null && !first.equals(failedSelector)) {
                healingCache.put(failedSelector, first);
            }
        } else {
            log.warn("[AI-HEAL] Failed to obtain alternative locators for: {}", failedSelector);
            AI_HEALING_LOGGER.warn("[AI-HEAL] No suggestions for: {}", failedSelector);
        }

        return suggestions;
    }

    // ------------------------------------------------------------------ prompt builder

    private String buildPrompt(String failedSelector, String cleanedHtml,
                               String focusHtml, String screenshotBase64Png) {
        String html = cleanedHtml == null ? "" : cleanedHtml;
        int max = config.getMaxHtmlChars();
        if (max >= 0 && html.length() > max) {
            html = html.substring(0, max);
        }

        String focused = (focusHtml == null) ? "" : focusHtml.trim();
        if (!focused.isEmpty()) {
            int focusMax = Math.min(20_000, Math.max(2000, max >= 0 ? max : 20_000));
            if (focused.length() > focusMax) {
                focused = focused.substring(0, focusMax);
            }
        }

        int selectorCount = Math.max(1, Math.min(config.getRetryCount(), 5));

        StringBuilder sb = new StringBuilder();
        sb.append("The selector '").append(failedSelector).append("' failed.\n");
        sb.append("Task: return ").append(selectorCount)
          .append(" replacement selectors (numbered 1-").append(selectorCount)
          .append(") that match the intended element.\n");
        sb.append("Mix CSS and XPath. Prefix XPath with xpath=\n");
        sb.append("Output ONLY the numbered list. No markdown, no explanation.\n\n");

        if (screenshotBase64Png != null && !screenshotBase64Png.isBlank()) {
            sb.append("SCREENSHOT:\n");
            sb.append("- A screenshot is attached to this request.\n");
            sb.append("- Use it to visually identify the intended element, then map it to the HTML.\n\n");
        }

        if (!focused.isEmpty()) {
            sb.append("FOCUS_HTML (most relevant snippet):\n");
            sb.append(focused).append("\n\n");
        }

        sb.append("FULL_PAGE_HTML:\n");
        sb.append(html);
        return sb.toString();
    }

    // ------------------------------------------------------------------ LLM call

    private String callLLM(String prompt, String screenshotBase64Png) {
        if (llmClient != null) {
            try {
                String response = (screenshotBase64Png != null && !screenshotBase64Png.isBlank())
                        ? llmClient.sendPrompt(prompt, screenshotBase64Png)
                        : llmClient.sendPrompt(prompt);
                if (response != null && !response.isEmpty()) {
                    response = response.replaceAll("```[\\w]*", "").replaceAll("```", "").trim();
                    return response;
                }
            } catch (Exception e) {
                log.error("[AI-HEAL] Error calling LLM", e);
                AI_HEALING_LOGGER.error("[AI-HEAL] Error calling LLM", e);
            }
        }

        log.warn("[AI-HEAL] LLM integration not available. Using fallback logic.");
        AI_HEALING_LOGGER.warn("[AI-HEAL] LLM integration not available.");
        return null;
    }

    // ------------------------------------------------------------------ response parser

    /** Line pattern: optional numbering (1. / 1) / 1:) then the actual selector. */
    private static final Pattern NUMBERED_LINE = Pattern.compile(
            "^\\s*\\d+[.):\\-]?\\s*(.+)$"
    );

    /**
     * Parses the LLM response into individual selectors.
     * Handles numbered lists, plain lines, and comma-separated values.
     */
    static List<String> parseMultipleSelectors(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Collections.emptyList();
        }

        List<String> selectors = new ArrayList<>();
        String[] lines = rawResponse.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m = NUMBERED_LINE.matcher(trimmed);
            String candidate = m.matches() ? m.group(1).trim() : trimmed;

            // Strip surrounding quotes if present
            if ((candidate.startsWith("\"") && candidate.endsWith("\"")) ||
                (candidate.startsWith("'") && candidate.endsWith("'"))) {
                candidate = candidate.substring(1, candidate.length() - 1).trim();
            }

            if (!candidate.isEmpty() && looksLikeSelector(candidate)) {
                selectors.add(candidate);
            }
        }

        // Cap at retryCount
        int maxCount = HealingConfig.getInstance().getRetryCount();
        if (selectors.size() > maxCount) {
            selectors = selectors.subList(0, maxCount);
        }

        return selectors;
    }

    /** Quick heuristic to check if a string looks like a CSS/XPath selector. */
    private static boolean looksLikeSelector(String s) {
        if (s.startsWith("xpath=")) return true;
        if (s.startsWith("//") || s.startsWith("(//")) return true;
        // CSS: contains at least one selector character
        return s.matches(".*[#.\\[:\\w>~+].*");
    }

    // ------------------------------------------------------------------ factory helpers

    private static LLMClient defaultClient() {
        HealingConfig cfg = HealingConfig.getInstance();
        String provider = cfg.getAiProvider();

        switch (provider.toUpperCase()) {
            case "CLAUDE":
                return new ClaudeClient();
            case "OPENAI":
                return new OpenAIClient();
            case "GEMINI":
            default:
                // Fall back to provider detection by API key presence
                String geminiKey = System.getenv("GEMINI_API_KEY");
                if (geminiKey != null && !geminiKey.isBlank()) {
                    return new GeminiClient();
                }
                String claudeKey = System.getenv("CLAUDE_API_KEY");
                if (claudeKey != null && !claudeKey.isBlank()) {
                    return new ClaudeClient();
                }
                String openaiKey = System.getenv("OPENAI_API_KEY");
                if (openaiKey != null && !openaiKey.isBlank()) {
                    return new OpenAIClient();
                }
                return new GeminiClient();
        }
    }

    private static HealingCache defaultCache() {
        String configuredPath = System.getProperty("healing.cache.path");
        Path path = (configuredPath == null || configuredPath.isBlank())
                ? Paths.get("healing-cache.json")
                : Paths.get(configuredPath);
        return new HealingCache(path);
    }
}
