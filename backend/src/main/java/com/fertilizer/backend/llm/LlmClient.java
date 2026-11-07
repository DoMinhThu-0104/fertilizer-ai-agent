package com.fertilizer.backend.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Gọi LLM qua endpoint /chat/completions (tương thích OpenAI), có hỗ trợ gọi công cụ.
 * Lỗi tạm thời (5xx, 429, timeout) được thử lại; hết lượt thì chuyển sang model dự phòng.
 */
@Component
public class LlmClient {

    public record Reply(String text, String model) {}

    private record Completion(Map<String, Object> message, String model) {}

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final int ATTEMPTS_PER_MODEL = 2;

    private final RestClient client;
    private final List<String> models;
    private final boolean configured;
    private final int maxSteps;
    private final JsonMapper mapper;

    @Autowired
    public LlmClient(
            @Value("${llm.base-url:}") String baseUrl,
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model:gemini-3.1-flash-lite}") String model,
            @Value("${llm.fallback-model:}") String fallbackModel,
            @Value("${llm.timeout-seconds:20}") int timeoutSeconds,
            @Value("${llm.max-tool-steps:5}") int maxSteps,
            JsonMapper mapper) {
        this(buildClient(baseUrl, apiKey, timeoutSeconds),
                buildModels(model, fallbackModel),
                !baseUrl.isBlank() && !apiKey.isBlank(),
                maxSteps,
                mapper);
    }

    /** Dùng cho test: truyền sẵn RestClient (có thể gắn máy chủ giả). */
    LlmClient(RestClient client, List<String> models, int maxSteps, JsonMapper mapper) {
        this(client, models, true, maxSteps, mapper);
    }

    private LlmClient(RestClient client, List<String> models, boolean configured,
                      int maxSteps, JsonMapper mapper) {
        this.client = client;
        this.models = models;
        this.configured = configured;
        this.maxSteps = maxSteps;
        this.mapper = mapper;
    }

    private static RestClient buildClient(String baseUrl, String apiKey, int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private static List<String> buildModels(String model, String fallbackModel) {
        List<String> list = new ArrayList<>();
        list.add(model);
        if (!fallbackModel.isBlank() && !fallbackModel.equals(model)) {
            list.add(fallbackModel);
        }
        return list;
    }

    public Reply chat(String userMessage) {
        return chat(null, userMessage, List.of());
    }

    public Reply chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, List.of());
    }

    /** Hỏi LLM; nếu LLM yêu cầu gọi công cụ thì chạy và lặp, tối đa maxSteps lượt gọi LLM. */
    public Reply chat(String systemPrompt, String userMessage, List<LlmTool> tools) {
        if (!configured) {
            throw new LlmException("Chưa cấu hình LLM_BASE_URL hoặc LLM_API_KEY");
        }

        Map<String, LlmTool> byName = new HashMap<>();
        List<Map<String, Object>> toolDefs = new ArrayList<>();
        for (LlmTool tool : tools) {
            byName.put(tool.name(), tool);
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            def.put("function", function);
            toolDefs.add(def);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(message("system", systemPrompt));
        }
        messages.add(message("user", userMessage));

        for (int step = 1; step <= maxSteps; step++) {
            Completion completion = complete(messages, toolDefs);
            Map<String, Object> answer = completion.message();

            if (answer.get("tool_calls") instanceof List<?> calls && !calls.isEmpty()) {
                // Giữ nguyên tin nhắn của LLM (kể cả phần chữ ký nội bộ của Gemini) rồi gửi kết quả công cụ
                messages.add(answer);
                for (Object call : calls) {
                    messages.add(runTool(call, byName));
                }
                continue;
            }
            if (answer.get("content") instanceof String text) {
                return new Reply(text, completion.model());
            }
            throw new LlmException("Phản hồi từ LLM không có nội dung");
        }
        throw new LlmException("Vượt quá " + maxSteps + " bước gọi công cụ");
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runTool(Object call, Map<String, LlmTool> byName) {
        String id = "";
        String name = "";
        String result;
        try {
            Map<?, ?> callMap = (Map<?, ?>) call;
            id = String.valueOf(callMap.get("id"));
            Map<?, ?> function = (Map<?, ?>) callMap.get("function");
            name = String.valueOf(function.get("name"));

            LlmTool tool = byName.get(name);
            if (tool == null) {
                result = "Lỗi: không có công cụ " + name;
            } else {
                Map<String, Object> args = new HashMap<>();
                if (function.get("arguments") instanceof String raw && !raw.isBlank()) {
                    args = mapper.readValue(raw, Map.class);
                }
                // Chỉ ghi tên tham số, không ghi giá trị (có thể chứa dữ liệu khách)
                log.info("Gọi công cụ {} với tham số {}", name, args.keySet());
                result = tool.execute(args);
            }
        } catch (RuntimeException e) {
            log.warn("Công cụ {} lỗi: {}", name, e.toString());
            result = "Lỗi khi chạy công cụ: " + e.getMessage();
        }

        Map<String, Object> toolMessage = new LinkedHashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", id);
        toolMessage.put("name", name);
        toolMessage.put("content", result == null ? "" : result);
        return toolMessage;
    }

    /** Một lượt gọi LLM, có thử lại và chuyển model dự phòng. */
    private Completion complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefs) {
        Exception last = null;
        for (String model : models) {
            for (int attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return new Completion(call(model, messages, toolDefs), model);
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

    private Map<String, Object> call(String model, List<Map<String, Object>> messages,
                                     List<Map<String, Object>> toolDefs) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (!toolDefs.isEmpty()) {
            body.put("tools", toolDefs);
        }

        Map<?, ?> res = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (res != null && res.get("choices") instanceof List<?> choices && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> choice
                && choice.get("message") instanceof Map<?, ?> message) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : message.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            return copy;
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
