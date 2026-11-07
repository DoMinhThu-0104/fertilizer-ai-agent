package com.fertilizer.backend.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

/** Gửi tin nhắn văn bản cho khách qua Messenger Send API của Meta. */
@Component
public class MessengerSender {

    /** Meta cho tối đa 2000 ký tự mỗi tin; chừa dư cho an toàn. */
    static final int MAX_LEN = 1900;

    private final RestClient client;
    private final String token;
    private final JsonMapper mapper;

    @Autowired
    public MessengerSender(
            @Value("${facebook.graph-base-url:https://graph.facebook.com/v23.0}") String baseUrl,
            @Value("${facebook.page-access-token:}") String token,
            JsonMapper mapper) {
        this(buildClient(baseUrl), token, mapper);
    }

    MessengerSender(RestClient client, String token, JsonMapper mapper) {
        this.client = client;
        this.token = token;
        this.mapper = mapper;
    }

    private static RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * Gửi một tin (tối đa MAX_LEN ký tự) cho khách.
     *
     * @return mã tin (message_id) do Meta trả về
     */
    public String send(String psid, String text) {
        if (token == null || token.isBlank()) {
            throw new MessengerException("Chưa cấu hình FB_PAGE_ACCESS_TOKEN");
        }
        String body = mapper.writeValueAsString(Map.of(
                "recipient", Map.of("id", psid),
                "messaging_type", "RESPONSE",
                "message", Map.of("text", text)));
        try {
            String raw = client.post()
                    .uri("/me/messages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            Map<?, ?> result = mapper.readValue(raw, Map.class);
            Object id = result.get("message_id");
            return id == null ? null : id.toString();
        } catch (RestClientResponseException e) {
            throw new MessengerException("Send API trả lỗi HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new MessengerException("Không kết nối được Send API");
        }
    }

    /** Cắt câu trả lời dài thành nhiều tin, ưu tiên cắt ở xuống dòng hoặc khoảng trắng. */
    public static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (rest.length() > MAX_LEN) {
            int cut = rest.lastIndexOf('\n', MAX_LEN);
            if (cut < MAX_LEN / 2) {
                cut = rest.lastIndexOf(' ', MAX_LEN);
            }
            if (cut < MAX_LEN / 2) {
                cut = MAX_LEN;
            }
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }
}
