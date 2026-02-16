package com.automation.ai;

import java.util.regex.Pattern;

/**
 * Best-effort redaction of sensitive values from HTML before sending it to an LLM
 * or writing prompts to an audit log.
 *
 * Note: This is not a complete DLP solution—treat it as a safety net.
 */
public final class HtmlRedactor {
    private HtmlRedactor() {}

    private static final Pattern PASSWORD_INPUT_VALUE = Pattern.compile(
            "(?is)(<input\\b[^>]*\\btype\\s*=\\s*(['\"])password\\2[^>]*\\bvalue\\s*=\\s*)(['\"])(.*?)\\3");

    private static final Pattern GENERIC_VALUE_ATTR = Pattern.compile(
            "(?is)(\\bvalue\\s*=\\s*)(['\"])([^'\"]{1,200})(\\2)");

    // Very rough JWT-like token shape (three base64url-ish segments separated by dots)
    private static final Pattern JWT_LIKE = Pattern.compile(
            "(?<![A-Za-z0-9_-])([A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})(?![A-Za-z0-9_-])");

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)([a-z0-9._%+-]{1,64})@([a-z0-9.-]{1,253}\\.[a-z]{2,})");

    public static String redact(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        String redacted = html;

        // Specifically redact password inputs' value
        redacted = PASSWORD_INPUT_VALUE.matcher(redacted)
                .replaceAll("$1$3[REDACTED_PASSWORD]$3");

        // Redact common JWT-like tokens
        redacted = JWT_LIKE.matcher(redacted)
                .replaceAll("[REDACTED_TOKEN]");

        // Redact emails (keep domain for debugging)
        redacted = EMAIL.matcher(redacted)
                .replaceAll("[REDACTED_EMAIL]@$2");

        // Redact value attributes that might contain secrets (best-effort; may over-redact)
        redacted = GENERIC_VALUE_ATTR.matcher(redacted)
                .replaceAll("$1$2[REDACTED]$2");

        return redacted;
    }
}

