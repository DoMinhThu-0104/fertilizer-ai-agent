package com.fertilizer.backend.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gọi LLM qua endpoint /chat/completions (tương thích OpenAI).
 * Lỗi tạm thời (5xx, 429, timeout) được thử lại; hết lượt thì chuyển sang model dự phòng.
 */
@Component
public class LlmClient {

    public record Reply(String text, String model) {}

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final int ATTEMPTS_PER_MODEL = 2;

    private final RestClient client;
    private final List<String> models = new ArrayList<>();
    private final boolean configured;

    public LlmClient(
            @Value("${llm.base-url:}") String baseUrl,
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model:gemini-3.1-flash-lite}") String model,
            @Value("${llm.fallback-model:}") String fallbackModel,
            @Value("${llm.timeout-seconds:20}") int timeoutSeconds) {
        this.configured = !baseUrl.isBlank() && !apiKey.isBlank();

        models.add(model);
        if (!fallbackModel.isBlank() && !fallbackModel.equals(model)) {
            models.add(fallbackModel);
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public Reply chat(String userMessage) {
        return chat(null, userMessage);
    }

    public Reply chat(String systemPrompt, String userMessage) {
        if (!configured) {
            throw new LlmException("Chưa cấu hình LLM_BASE_URL hoặc LLM_API_KEY");
        }

        Exception last = null;
        for (String model : models) {
            for (int attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return new Reply(call(model, systemPrompt, userMessage), model);
                } catch (RestClientResponseException e) {
                    last = e;
                    int status = e.getStatusCode().value();
                    log.warn("LLM {} lỗi HTTP {} (lần {})", model, status, attempt);
                    boolean retryable = status >= 500 || status == 429;
                    if (!retryable) {
                        break; // 400/401/403/404: thử lại vô ích, qua model kế tiếp
                    }
                } catch (ResourceAccessException e) {
                    last = e;
                    log.warn("LLM {} timeout/lỗi mạng (lần {}): {}", model, attempt, e.getMessage());
                }
                if (attempt < ATTEMPTS_PER_MODEL) {
                    pause(1000L * attempt);
                }
            }
        }
        throw new LlmException("Không gọi được LLM sau khi thử các model " + models, last);
    }

    private String call(String model, String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        Map<String, Object> body = Map.of("model", model, "messages", messages);

        Map<?, ?> res = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (res != null && res.get("choices") instanceof List<?> choices && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> choice
                && choice.get("message") instanceof Map<?, ?> message
                && message.get("content") instanceof String content) {
            return content;
        }
        throw new LlmException("Phản hồi từ LLM không có nội dung");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
