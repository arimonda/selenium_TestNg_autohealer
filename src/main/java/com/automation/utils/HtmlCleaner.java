package com.automation.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * HTML cleaner that produces a simplified DOM for AI consumption.
 * <p>
 * Removes noise that wastes tokens and confuses LLMs:
 * <ul>
 *   <li>Script tags and their content</li>
 *   <li>Style tags and their content</li>
 *   <li>SVG tags and their content</li>
 *   <li>Meta/link/noscript tags</li>
 *   <li>HTML comments</li>
 *   <li>Inline event handlers (onclick, onload, etc.)</li>
 *   <li>data-* attributes that look like hashes/random IDs</li>
 *   <li>Excessive whitespace</li>
 * </ul>
 */
@Slf4j
public class HtmlCleaner {

    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_TAG = Pattern.compile(
            "(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern SVG_TAG = Pattern.compile(
            "(?is)<svg\\b[^>]*>.*?</svg>");
    private static final Pattern NOSCRIPT_TAG = Pattern.compile(
            "(?is)<noscript\\b[^>]*>.*?</noscript>");
    private static final Pattern META_TAG = Pattern.compile(
            "(?i)<meta\\b[^>]*/?>");
    private static final Pattern LINK_TAG = Pattern.compile(
            "(?i)<link\\b[^>]*/?>");
    private static final Pattern HTML_COMMENTS = Pattern.compile(
            "<!--[\\s\\S]*?-->");
    private static final Pattern INLINE_EVENTS = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1");
    private static final Pattern RANDOM_DATA_ATTRS = Pattern.compile(
            "(?i)\\s+data-[a-z0-9-]+=\\s*['\"][a-f0-9]{8,}['\"]");
    private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile(
            "\\s{2,}");

    /**
     * Cleans raw HTML by removing noise elements.
     *
     * @param html raw page source
     * @return simplified HTML suitable for AI prompts
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        String cleaned = html;

        // Remove elements that are pure noise for selector generation
        cleaned = SCRIPT_TAG.matcher(cleaned).replaceAll("");
        cleaned = STYLE_TAG.matcher(cleaned).replaceAll("");
        cleaned = SVG_TAG.matcher(cleaned).replaceAll("");
        cleaned = NOSCRIPT_TAG.matcher(cleaned).replaceAll("");
        cleaned = META_TAG.matcher(cleaned).replaceAll("");
        cleaned = LINK_TAG.matcher(cleaned).replaceAll("");
        cleaned = HTML_COMMENTS.matcher(cleaned).replaceAll("");

        // Remove inline event handlers (onclick, onload, etc.)
        cleaned = INLINE_EVENTS.matcher(cleaned).replaceAll("");

        // Remove data attributes that look like random hashes (typically framework-generated)
        cleaned = RANDOM_DATA_ATTRS.matcher(cleaned).replaceAll("");

        // Collapse whitespace
        cleaned = EXCESSIVE_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        log.debug("HTML cleaned: {} characters reduced to {} characters", html.length(), cleaned.length());
        return cleaned;
    }

    /**
     * Produces an even more aggressive simplification: strips ALL attributes
     * except a curated whitelist (id, class, name, type, role, aria-*, data-testid, href, for, placeholder, title, value).
     * <p>
     * Useful when the HTML is very large and token budget is tight.
     *
     * @param html pre-cleaned HTML
     * @return skeleton DOM
     */
    public static String toSkeletonDom(String html) {
        if (html == null || html.isEmpty()) return html;

        // First apply standard cleaning
        String cleaned = cleanHtml(html);

        // Keep only whitelisted attributes
        // This regex matches attr="value" or attr='value' pairs
        Pattern allAttrs = Pattern.compile(
                "(?i)\\s+(?!(?:id|class|name|type|role|aria-[a-z]+|data-testid|data-test|href|for|placeholder|title|value|action|method)\\b)[a-z][a-z0-9_-]*\\s*=\\s*(['\"]).*?\\1");
        cleaned = allAttrs.matcher(cleaned).replaceAll("");

        // Remove empty attribute artifacts
        cleaned = cleaned.replaceAll("\\s+>", ">");

        log.debug("Skeleton DOM: {} characters", cleaned.length());
        return cleaned;
    }
}
