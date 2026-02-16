package com.automation.ai;

/**
 * Interface for LLM clients (OpenAI, Anthropic, etc.)
 * Implement this interface to integrate with your preferred LLM provider
 */
public interface LLMClient {
    /**
     * Sends a prompt to the LLM and returns the response
     * @param prompt The prompt to send
     * @return The LLM's response (should be a raw CSS/XPath selector)
     */
    String sendPrompt(String prompt);

    /**
     * Optional multimodal prompt: prompt + screenshot image (base64-encoded).
     * Implementations that don't support images can ignore the image and fallback to text-only.
     *
     * @param prompt prompt text
     * @param screenshotBase64Png base64 PNG bytes (no data: prefix)
     * @return selector string (CSS or xpath=...)
     */
    default String sendPrompt(String prompt, String screenshotBase64Png) {
        return sendPrompt(prompt);
    }
}

