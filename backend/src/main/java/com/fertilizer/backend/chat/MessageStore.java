package com.fertilizer.backend.chat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lưu tin nhắn vào hội thoại tương ứng (tạo hội thoại nếu khách nhắn lần đầu). */
@Service
public class MessageStore {

    private final JdbcClient jdbc;

    public MessageStore(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * Lưu tin khách gửi đến.
     *
     * @return true nếu là tin mới, false nếu mid đã có (Facebook gửi lại cùng một tin)
     */
    @Transactional
    public boolean saveIncoming(MessengerEvent event) {
        Long conversationId = jdbc.sql("""
                INSERT INTO conversation (page_id, sender_id) VALUES (:page, :sender)
                ON CONFLICT (page_id, sender_id) DO UPDATE SET last_message_at = now()
                RETURNING id
                """)
                .param("page", event.pageId())
                .param("sender", event.senderId())
                .query(Long.class)
                .single();

        int inserted = jdbc.sql("""
                INSERT INTO message (conversation_id, direction, external_id, body, sent_at)
                VALUES (:conv, 'IN', :mid, :body, :sentAt)
                ON CONFLICT (external_id) DO NOTHING
                """)
                .param("conv", conversationId)
                .param("mid", event.mid())
                .param("body", event.text())
                .param("sentAt", OffsetDateTime.ofInstant(event.sentAt(), ZoneOffset.UTC))
                .update();
        return inserted == 1;
    }
}
