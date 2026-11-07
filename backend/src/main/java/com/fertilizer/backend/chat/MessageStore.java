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

    /** Lưu tin hệ thống gửi cho khách (direction OUT) vào đúng hội thoại. */
    @Transactional
    public void saveOutgoing(String pageId, String senderId, String mid, String text) {
        jdbc.sql("""
                INSERT INTO message (conversation_id, direction, external_id, body, sent_at)
                SELECT c.id, 'OUT', :mid, :body, now()
                FROM conversation c
                WHERE c.page_id = :page AND c.sender_id = :sender
                ON CONFLICT (external_id) DO NOTHING
                """)
                .param("page", pageId)
                .param("sender", senderId)
                .param("mid", mid)
                .param("body", text)
                .update();
        jdbc.sql("UPDATE conversation SET last_message_at = now() WHERE page_id = :page AND sender_id = :sender")
                .param("page", pageId)
                .param("sender", senderId)
                .update();
    }

    /** Hội thoại đang do nhân viên xử lý (status = NGUOI) thì AI không trả lời. */
    public boolean isHandedToHuman(String pageId, String senderId) {
        return jdbc.sql("SELECT status FROM conversation WHERE page_id = :page AND sender_id = :sender")
                .param("page", pageId)
                .param("sender", senderId)
                .query(String.class)
                .optional()
                .map("NGUOI"::equals)
                .orElse(false);
    }
}
