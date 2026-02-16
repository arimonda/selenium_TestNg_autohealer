package com.automation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example implementation of LLMClient using OpenAI API
 * To use this, set OPENAI_API_KEY in your .env file or as an environment variable
 */
@Slf4j
public class OpenAIClient implements LLMClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int DEFAULT_MAX_IMAGE_BASE64_CHARS = 2_000_000;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIClient() {
        String key = null;
        String configuredModel = null;
        String configuredSystemPrompt = null;
        try {
            Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
            key = dotenv.get("OPENAI_API_KEY");
            configuredModel = dotenv.get("OPENAI_MODEL");
            configuredSystemPrompt = dotenv.get("OPENAI_SYSTEM_PROMPT");
        } catch (Exception e) {
            log.debug("Could not load .env file, trying environment variables", e);
        }
        
        // Fallback to environment variable
        if (key == null || key.isEmpty()) {
            key = System.getenv("OPENAI_API_KEY");
        }

        // Model selection (system property > .env > env var > default)
        String sysModel = System.getProperty("openai.model");
        if (sysModel != null && !sysModel.isBlank()) {
            configuredModel = sysModel;
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = System.getenv("OPENAI_MODEL");
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = "gpt-5.2";
        }

        // System prompt selection (system property > .env > env var > default)
        String sysSystemPrompt = System.getProperty("openai.systemPrompt");
        if (sysSystemPrompt != null && !sysSystemPrompt.isBlank()) {
            configuredSystemPrompt = sysSystemPrompt;
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = System.getenv("OPENAI_SYSTEM_PROMPT");
        }
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            configuredSystemPrompt = defaultSystemPrompt();
        }
        
        this.apiKey = key;
        this.model = configuredModel.trim();
        this.systemPrompt = configuredSystemPrompt.trim();
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OPENAI_API_KEY not found in .env file or environment variables. LLM integration will not work.");
        }
        log.info("OpenAI model configured as: {}", this.model);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sendPrompt(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("OpenAI API key not configured");
            return null;
        }

        try {
            String requestBody = buildRequestBody(prompt);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String content = jsonResponse
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();
                return content.trim();
            } else {
                log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            return null;
        }
    }

    @Override
    public String sendPrompt(String prompt, String screenshotBase64Png) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("OpenAI API key not configured");
            return null;
        }
        if (screenshotBase64Png == null || screenshotBase64Png.isBlank()) {
            return sendPrompt(prompt);
        }

        int max = maxImageBase64Chars();
        if (max >= 0 && screenshotBase64Png.length() > max) {
            log.warn("Screenshot base64 too large ({} chars). Falling back to text-only healing.", screenshotBase64Png.length());
            return sendPrompt(prompt);
        }

        try {
            String requestBody = buildRequestBody(prompt, screenshotBase64Png);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(90))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String content = jsonResponse
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();
                return content.trim();
            } else {
                log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to call OpenAI API (with screenshot)", e);
            return null;
        }
    }

    private String buildRequestBody(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 300);
            
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            
            requestBody.put("messages", List.of(systemMessage, userMessage));
            
            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to build request body", e);
            return "{}";
        }
    }

    private String buildRequestBody(String prompt, String screenshotBase64Png) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 300);

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);

            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/png;base64," + screenshotBase64Png);
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);

            userMessage.put("content", List.of(textPart, imagePart));

            requestBody.put("messages", List.of(systemMessage, userMessage));

            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to build request body (with screenshot)", e);
            return "{}";
        }
    }

    private int maxImageBase64Chars() {
        String sys = System.getProperty("openai.image.maxBase64Chars");
        if (sys != null && !sys.isBlank()) {
            try {
                return Integer.parseInt(sys.trim());
            } catch (Exception ignored) {
                return DEFAULT_MAX_IMAGE_BASE64_CHARS;
            }
        }
        String env = System.getenv("OPENAI_IMAGE_MAX_BASE64_CHARS");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (Exception ignored) {
                return DEFAULT_MAX_IMAGE_BASE64_CHARS;
            }
        }
        return DEFAULT_MAX_IMAGE_BASE64_CHARS;
    }

    private static String defaultSystemPrompt() {
        return """
            You are an expert UI test automation assistant.

            Your task: Given a failed selector, FULL_PAGE_HTML, and optionally a screenshot, \
            return replacement selectors that match the intended element.

            Output rules (MUST FOLLOW):
            - Return EXACTLY 5 alternative selectors, one per line, numbered 1-5.
            - Mix of CSS selectors and XPath selectors.
            - Prefer CSS. Use XPath only if CSS is impractical.
            - If you return XPath, prefix it with: xpath=
            - Do NOT include markdown, backticks, quotes, explanations, or extra whitespace.

            Selector quality rules:
            - Prefer stable attributes: data-testid, data-test, aria-label, role, name, placeholder, type, href, for, title.
            - Avoid brittle selectors: nth-child, deeply nested chains, dynamic ids/classes (random hashes), absolute XPaths.
            - Make it as specific as needed to be unique, but keep it robust.
            """;
    }
}

