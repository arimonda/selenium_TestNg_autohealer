package com.automation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Anthropic Claude implementation of {@link LLMClient}.
 * <p>
 * Endpoint: POST https://api.anthropic.com/v1/messages
 * <p>
 * Config (system property &gt; .env &gt; env var &gt; default):
 * <ul>
 *   <li>API key: CLAUDE_API_KEY</li>
 *   <li>Model: claude.model / CLAUDE_MODEL (default: claude-sonnet-4-20250514)</li>
 *   <li>System prompt: claude.systemPrompt / CLAUDE_SYSTEM_PROMPT</li>
 *   <li>Screenshot cap: claude.image.maxBase64Chars / CLAUDE_IMAGE_MAX_BASE64_CHARS (default: 2,000,000)</li>
 * </ul>
 */
@Slf4j
public class ClaudeClient implements LLMClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_IMAGE_BASE64_CHARS = 2_000_000;

    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeClient() {
        String key = null;
        String configuredModel = null;
        String configuredSystemPrompt = null;

        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            key = dotenv.get("CLAUDE_API_KEY");
            configuredModel = dotenv.get("CLAUDE_MODEL");
            configuredSystemPrompt = dotenv.get("CLAUDE_SYSTEM_PROMPT");
        } catch (Exception e) {
            log.debug("Could not load .env for Claude config; using environment variables", e);
        }

        if (key == null || key.isBlank()) {
            key = System.getenv("CLAUDE_API_KEY");
        }

        String sysModel = System.getProperty("claude.model");
        if (sysModel != null && !sysModel.isBlank()) {
            configuredModel = sysModel;
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = System.getenv("CLAUDE_MODEL");
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = "claude-sonnet-4-20250514";
        }

        String sysPrompt = System.getProperty("claude.systemPrompt");
        if (sysPrompt != null && !sysPrompt.isBlank()) {
            configuredSystemPrompt = sysPrompt;
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = System.getenv("CLAUDE_SYSTEM_PROMPT");
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = defaultSystemPrompt();
        }

        this.apiKey = key;
        this.model = configuredModel.trim();
        this.systemPrompt = configuredSystemPrompt.trim();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CLAUDE_API_KEY not found. Claude healing will not work.");
        }
        log.info("Claude model configured as: {}", this.model);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sendPrompt(String prompt) {
        return sendPrompt(prompt, null);
    }

    @Override
    public String sendPrompt(String prompt, String screenshotBase64Png) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Claude API key not configured");
            return null;
        }

        String img = (screenshotBase64Png == null) ? null : screenshotBase64Png.trim();
        if (img != null && !img.isEmpty()) {
            int max = maxImageBase64Chars();
            if (max >= 0 && img.length() > max) {
                log.warn("Screenshot base64 too large ({} chars). Falling back to text-only.", img.length());
                img = null;
            }
        }

        try {
            String requestBody = (img == null || img.isEmpty())
                    ? buildRequestBody(prompt)
                    : buildRequestBody(prompt, img);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(90))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode contentArr = json.path("content");
                if (contentArr.isArray() && !contentArr.isEmpty()) {
                    String text = contentArr.get(0).path("text").asText();
                    return (text == null) ? null : text.trim();
                }
                return null;
            }

            log.error("Claude API error: {} - {}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Claude API", e);
            return null;
        }
    }

    // ------------------------------------------------------------------ request builders

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model", model);
            req.put("max_tokens", 300);
            req.put("system", systemPrompt);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            req.set("messages", messages);

            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Failed to build Claude request body", e);
            return "{}";
        }
    }

    private String buildRequestBody(String prompt, String screenshotBase64Png) {
        try {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model", model);
            req.put("max_tokens", 300);
            req.put("system", systemPrompt);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");

            ArrayNode content = objectMapper.createArrayNode();

            // Image block
            ObjectNode imageBlock = objectMapper.createObjectNode();
            imageBlock.put("type", "image");
            ObjectNode source = objectMapper.createObjectNode();
            source.put("type", "base64");
            source.put("media_type", "image/png");
            source.put("data", screenshotBase64Png);
            imageBlock.set("source", source);
            content.add(imageBlock);

            // Text block
            ObjectNode textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);
            content.add(textBlock);

            userMsg.set("content", content);
            messages.add(userMsg);
            req.set("messages", messages);

            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Failed to build Claude request body (with screenshot)", e);
            return "{}";
        }
    }

    // ------------------------------------------------------------------ helpers

    private int maxImageBase64Chars() {
        String sys = System.getProperty("claude.image.maxBase64Chars");
        if (sys != null && !sys.isBlank()) {
            try { return Integer.parseInt(sys.trim()); }
            catch (Exception ignored) { return DEFAULT_MAX_IMAGE_BASE64_CHARS; }
        }
        String env = System.getenv("CLAUDE_IMAGE_MAX_BASE64_CHARS");
        if (env != null && !env.isBlank()) {
            try { return Integer.parseInt(env.trim()); }
            catch (Exception ignored) { return DEFAULT_MAX_IMAGE_BASE64_CHARS; }
        }
        return DEFAULT_MAX_IMAGE_BASE64_CHARS;
    }

    private static String defaultSystemPrompt() {
        return String.join("\n",
                "You are an expert UI test automation assistant.",
                "",
                "Your task: Given a failed selector, FULL_PAGE_HTML, and optionally a screenshot,",
                "return replacement selectors that match the intended element.",
                "",
                "Output rules (MUST FOLLOW):",
                "- Return EXACTLY 5 alternative selectors, one per line, numbered 1-5.",
                "- Mix of CSS selectors and XPath selectors.",
                "- Prefix XPath selectors with: xpath=",
                "- Do NOT include markdown, backticks, quotes, explanations, or extra whitespace.",
                "",
                "Selector quality rules:",
                "- Prefer stable attributes: data-testid, data-test, aria-label, role, name, placeholder, type, href, for, title.",
                "- Avoid brittle selectors: nth-child, deeply nested chains, dynamic ids/classes (random hashes), absolute XPaths.",
                "- Make it as specific as needed to be unique, but keep it robust."
        );
    }
}
