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
 * Gemini implementation of {@link LLMClient} using the Google Generative Language API (v1beta).
 *
 * Endpoint:
 * - POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=...
 *
 * Config (system property > .env > env var > default):
 * - API key: GEMINI_API_KEY
 * - Model: gemini.model / GEMINI_MODEL (default: gemini-2.0-flash)
 * - System prompt: gemini.systemPrompt / GEMINI_SYSTEM_PROMPT
 * - Screenshot cap: gemini.image.maxBase64Chars / GEMINI_IMAGE_MAX_BASE64_CHARS (default: 2,000,000)
 */
@Slf4j
public class GeminiClient implements LLMClient {
    private static final String API_HOST = "https://generativelanguage.googleapis.com";
    private static final String API_PATH_TEMPLATE = "/v1beta/models/%s:generateContent?key=%s";
    private static final int DEFAULT_MAX_IMAGE_BASE64_CHARS = 2_000_000;

    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClient() {
        String key = null;
        String configuredModel = null;
        String configuredSystemPrompt = null;

        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            key = dotenv.get("GEMINI_API_KEY");
            configuredModel = dotenv.get("GEMINI_MODEL");
            configuredSystemPrompt = dotenv.get("GEMINI_SYSTEM_PROMPT");
        } catch (Exception e) {
            log.debug("Could not load .env for Gemini config; using environment variables", e);
        }

        if (key == null || key.isBlank()) {
            key = System.getenv("GEMINI_API_KEY");
        }

        String sysModel = System.getProperty("gemini.model");
        if (sysModel != null && !sysModel.isBlank()) {
            configuredModel = sysModel;
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = System.getenv("GEMINI_MODEL");
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = "gemini-2.0-flash";
        }

        String sysPrompt = System.getProperty("gemini.systemPrompt");
        if (sysPrompt != null && !sysPrompt.isBlank()) {
            configuredSystemPrompt = sysPrompt;
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = System.getenv("GEMINI_SYSTEM_PROMPT");
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = defaultSystemPrompt();
        }

        this.apiKey = key;
        this.model = configuredModel.trim();
        this.systemPrompt = configuredSystemPrompt.trim();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY not found. Gemini healing will not work.");
        }
        log.info("Gemini model configured as: {}", this.model);

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
            log.error("Gemini API key not configured");
            return null;
        }

        String img = (screenshotBase64Png == null) ? null : screenshotBase64Png.trim();
        if (img != null && !img.isEmpty()) {
            int max = maxImageBase64Chars();
            if (max >= 0 && img.length() > max) {
                log.warn("Screenshot base64 too large ({} chars). Falling back to text-only healing.", img.length());
                img = null;
            }
        }

        try {
            String requestBody = (img == null || img.isEmpty())
                ? buildRequestBody(prompt)
                : buildRequestBody(prompt, img);

            String path = String.format(API_PATH_TEMPLATE, urlEncodePath(model), apiKey);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_HOST + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(90))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode text = json.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                String out = text.isMissingNode() ? null : text.asText();
                return (out == null) ? null : out.trim();
            }

            log.error("Gemini API error: {} - {}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Gemini API", e);
            return null;
        }
    }

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode req = objectMapper.createObjectNode();

            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
            systemInstruction.set("parts", sysParts);
            req.set("systemInstruction", systemInstruction);

            ObjectNode user = objectMapper.createObjectNode();
            user.put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode().put("text", prompt));
            user.set("parts", parts);

            ArrayNode contents = objectMapper.createArrayNode();
            contents.add(user);
            req.set("contents", contents);

            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Failed to build Gemini request body", e);
            return "{}";
        }
    }

    private String buildRequestBody(String prompt, String screenshotBase64Png) {
        try {
            ObjectNode req = objectMapper.createObjectNode();

            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
            systemInstruction.set("parts", sysParts);
            req.set("systemInstruction", systemInstruction);

            ObjectNode user = objectMapper.createObjectNode();
            user.put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode().put("text", prompt));

            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mime_type", "image/png");
            inlineData.put("data", screenshotBase64Png);
            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.set("inline_data", inlineData);
            parts.add(imagePart);

            user.set("parts", parts);

            ArrayNode contents = objectMapper.createArrayNode();
            contents.add(user);
            req.set("contents", contents);

            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Failed to build Gemini request body (with screenshot)", e);
            return "{}";
        }
    }

    private int maxImageBase64Chars() {
        String sys = System.getProperty("gemini.image.maxBase64Chars");
        if (sys != null && !sys.isBlank()) {
            try {
                return Integer.parseInt(sys.trim());
            } catch (Exception ignored) {
                return DEFAULT_MAX_IMAGE_BASE64_CHARS;
            }
        }
        String env = System.getenv("GEMINI_IMAGE_MAX_BASE64_CHARS");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (Exception ignored) {
                return DEFAULT_MAX_IMAGE_BASE64_CHARS;
            }
        }
        return DEFAULT_MAX_IMAGE_BASE64_CHARS;
    }

    private String urlEncodePath(String value) {
        return value.replace(" ", "%20");
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
            "- Prefer CSS. Use XPath only if CSS is impractical.",
            "- If you return XPath, prefix it with: xpath=",
            "- Do NOT include markdown, backticks, quotes, explanations, or extra whitespace.",
            "",
            "Selector quality rules:",
            "- Prefer stable attributes: data-testid, data-test, aria-label, role, name, placeholder, type, href, for, title.",
            "- Avoid brittle selectors: nth-child, deeply nested chains, dynamic ids/classes (random hashes), absolute XPaths.",
            "- Make it as specific as needed to be unique, but keep it robust."
        );
    }
}

