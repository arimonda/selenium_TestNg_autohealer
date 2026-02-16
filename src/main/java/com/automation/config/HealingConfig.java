package com.automation.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized configuration loader for the AI Self-Healing Engine.
 * <p>
 * Load order (highest precedence first):
 * <ol>
 *   <li>System properties (-Dhealing.enabled=false)</li>
 *   <li>healing-config.yaml on the classpath</li>
 *   <li>Hard-coded defaults</li>
 * </ol>
 * <p>
 * API keys are intentionally loaded from environment / .env (never from YAML).
 */
@Slf4j
@Getter
public final class HealingConfig {

    private static volatile HealingConfig instance;

    // --- Healing ---
    private boolean healingEnabled;
    private int retryCount;
    private boolean autoUpdateCode;
    private int maxHtmlChars;

    // --- AI ---
    private String aiProvider;   // GEMINI | OPENAI | CLAUDE
    private String geminiModel;
    private String openaiModel;
    private String claudeModel;

    // --- Screenshot ---
    private boolean screenshotEnabled;
    private String screenshotDirectory;

    // --- DOM ---
    private boolean domSimplified;
    private boolean domCaptureCss;

    private HealingConfig() {
        loadDefaults();
        loadYaml();
        applySystemPropertyOverrides();
        logConfiguration();
    }

    public static HealingConfig getInstance() {
        if (instance == null) {
            synchronized (HealingConfig.class) {
                if (instance == null) {
                    instance = new HealingConfig();
                }
            }
        }
        return instance;
    }

    /** Reload configuration (useful for tests). */
    public static synchronized void reload() {
        instance = null;
        getInstance();
    }

    // ------------------------------------------------------------------ defaults
    private void loadDefaults() {
        healingEnabled = true;
        retryCount = 5;
        autoUpdateCode = true;
        maxHtmlChars = 200_000;
        aiProvider = "GEMINI";
        geminiModel = "gemini-2.0-flash";
        openaiModel = "gpt-4o";
        claudeModel = "claude-sonnet-4-20250514";
        screenshotEnabled = true;
        screenshotDirectory = "logs/screenshots";
        domSimplified = true;
        domCaptureCss = true;
    }

    // ------------------------------------------------------------------ YAML
    @SuppressWarnings("unchecked")
    private void loadYaml() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("healing-config.yaml")) {
            if (is == null) {
                log.info("healing-config.yaml not found on classpath; using defaults.");
                return;
            }
            // Minimal YAML parsing without external dependency -- supports flat key: value
            // For nested YAML we do a simple recursive map parse.
            Map<String, Object> root = parseSimpleYaml(is);
            if (root == null || root.isEmpty()) return;

            Map<String, Object> healing = asMap(root.get("healing"));
            if (healing != null) {
                healingEnabled = asBool(healing.get("enabled"), healingEnabled);
                retryCount = asInt(healing.get("retry.count"), retryCount);
                autoUpdateCode = asBool(healing.get("auto.update.code"), autoUpdateCode);
                maxHtmlChars = asInt(healing.get("max.html.chars"), maxHtmlChars);
            }

            Map<String, Object> ai = asMap(root.get("ai"));
            if (ai != null) {
                String prov = asString(ai.get("provider"));
                if (prov != null) aiProvider = prov.toUpperCase();
                Map<String, Object> gem = asMap(ai.get("gemini"));
                if (gem != null) geminiModel = asString(gem.get("model"), geminiModel);
                Map<String, Object> oai = asMap(ai.get("openai"));
                if (oai != null) openaiModel = asString(oai.get("model"), openaiModel);
                Map<String, Object> cl = asMap(ai.get("claude"));
                if (cl != null) claudeModel = asString(cl.get("model"), claudeModel);
            }

            Map<String, Object> ss = asMap(root.get("screenshot"));
            if (ss != null) {
                screenshotEnabled = asBool(ss.get("enabled"), screenshotEnabled);
                screenshotDirectory = asString(ss.get("directory"), screenshotDirectory);
            }

            Map<String, Object> dom = asMap(root.get("dom"));
            if (dom != null) {
                domSimplified = asBool(dom.get("simplified"), domSimplified);
                domCaptureCss = asBool(dom.get("capture.css"), domCaptureCss);
            }

            log.info("healing-config.yaml loaded successfully.");
        } catch (Exception e) {
            log.warn("Failed to load healing-config.yaml; using defaults.", e);
        }
    }

    // ------------------------------------------------------------------ system props
    private void applySystemPropertyOverrides() {
        healingEnabled = sysBool("healing.enabled", healingEnabled);
        retryCount = sysInt("healing.retry.count", retryCount);
        autoUpdateCode = sysBool("healing.auto.update.code", autoUpdateCode);
        maxHtmlChars = sysInt("healing.max.html.chars", maxHtmlChars);
        String prov = System.getProperty("ai.provider");
        if (prov != null && !prov.isBlank()) aiProvider = prov.trim().toUpperCase();
        screenshotEnabled = sysBool("healing.screenshot.enabled", screenshotEnabled);
        domSimplified = sysBool("healing.dom.simplified", domSimplified);
        domCaptureCss = sysBool("healing.dom.capture.css", domCaptureCss);
    }

    // ------------------------------------------------------------------ logging
    private void logConfiguration() {
        log.info("=== AI Self-Healing Configuration ===");
        log.info("  healing.enabled       = {}", healingEnabled);
        log.info("  retry.count           = {}", retryCount);
        log.info("  auto.update.code      = {}", autoUpdateCode);
        log.info("  ai.provider           = {}", aiProvider);
        log.info("  screenshot.enabled    = {}", screenshotEnabled);
        log.info("  dom.simplified        = {}", domSimplified);
        log.info("  dom.capture.css       = {}", domCaptureCss);
        log.info("=====================================");
    }

    // ------------------------------------------------------------------ helpers

    /** Minimal YAML parser that handles 2-level nesting with indentation. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSimpleYaml(InputStream is) {
        try {
            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\\R");
            Map<String, Object> root = new LinkedHashMap<>();
            String currentSection = null;
            String currentSubSection = null;
            Map<String, Object> sectionMap = null;
            Map<String, Object> subSectionMap = null;

            for (String rawLine : lines) {
                String line = rawLine;
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;

                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') indent++;
                String trimmed = line.trim();
                if (!trimmed.contains(":")) continue;

                int colonIdx = trimmed.indexOf(':');
                String key = trimmed.substring(0, colonIdx).trim();
                String value = colonIdx < trimmed.length() - 1 ? trimmed.substring(colonIdx + 1).trim() : "";

                // Remove inline comments
                if (value.contains(" #")) {
                    value = value.substring(0, value.indexOf(" #")).trim();
                }

                if (indent == 0) {
                    // Top-level section
                    if (value.isEmpty()) {
                        currentSection = key;
                        currentSubSection = null;
                        sectionMap = new LinkedHashMap<>();
                        subSectionMap = null;
                        root.put(key, sectionMap);
                    } else {
                        root.put(key, parseValue(value));
                        currentSection = null;
                        sectionMap = null;
                    }
                } else if (indent <= 4 && sectionMap != null) {
                    if (value.isEmpty()) {
                        // Sub-section
                        currentSubSection = key;
                        subSectionMap = new LinkedHashMap<>();
                        sectionMap.put(key, subSectionMap);
                    } else {
                        sectionMap.put(key, parseValue(value));
                        currentSubSection = null;
                        subSectionMap = null;
                    }
                } else if (subSectionMap != null) {
                    subSectionMap.put(key, parseValue(value));
                }
            }
            return root;
        } catch (Exception e) {
            log.warn("Simple YAML parse failed", e);
            return null;
        }
    }

    private static Object parseValue(String val) {
        if (val == null || val.isEmpty()) return "";
        if ("true".equalsIgnoreCase(val)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(val)) return Boolean.FALSE;
        try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        try { return Long.parseLong(val); } catch (NumberFormatException ignored) {}
        return val;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String asString(Object o, String def) {
        String s = asString(o);
        return (s == null || s.isEmpty()) ? def : s;
    }

    private static boolean sysBool(String key, boolean def) {
        String v = System.getProperty(key);
        return (v != null && !v.isBlank()) ? Boolean.parseBoolean(v.trim()) : def;
    }

    private static int sysInt(String key, int def) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) {
            try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }
}
