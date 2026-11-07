package com.fertilizer.backend.chat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Đọc nội dung webhook của Meta, chỉ lấy tin nhắn văn bản của khách.
 * Bỏ qua: tin do Page tự gửi (echo), báo đã nhận/đã đọc, postback, tin chỉ có ảnh hoặc tệp.
 * Không bao giờ ghi nội dung tin nhắn vào log.
 */
@Component
public class MessengerPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(MessengerPayloadParser.class);

    private final JsonMapper mapper;

    public MessengerPayloadParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public List<MessengerEvent> parse(byte[] raw) {
        List<MessengerEvent> events = new ArrayList<>();

        Object root;
        try {
            root = mapper.readValue(new String(raw, StandardCharsets.UTF_8), Object.class);
        } catch (RuntimeException e) {
            log.warn("Webhook Facebook: không đọc được JSON");
            return events;
        }
        if (!(root instanceof Map<?, ?> body) || !"page".equals(body.get("object"))
                || !(body.get("entry") instanceof List<?> entries)) {
            return events;
        }

        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> entry)
                    || !(entry.get("messaging") instanceof List<?> items)) {
                continue;
            }
            String pageId = str(entry.get("id"));

            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> item)
                        || !(item.get("message") instanceof Map<?, ?> message)) {
                    continue; // báo đã nhận, đã đọc, postback...
                }
                if (Boolean.TRUE.equals(message.get("is_echo"))) {
                    continue; // tin do chính Page gửi
                }
                String senderId = item.get("sender") instanceof Map<?, ?> sender ? str(sender.get("id")) : null;
                String mid = str(message.get("mid"));
                String text = str(message.get("text"));
                if (pageId == null || senderId == null || mid == null || text == null || text.isBlank()) {
                    continue;
                }
                Instant sentAt = item.get("timestamp") instanceof Number n
                        ? Instant.ofEpochMilli(n.longValue())
                        : Instant.now();
                events.add(new MessengerEvent(pageId, senderId, mid, text, sentAt));
            }
        }
        return events;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
