package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Cần PostgreSQL đang chạy (docker compose up -d). Dùng id ngẫu nhiên nên không cần dọn dữ liệu. */
@SpringBootTest
class MessageStoreTest {

    @Autowired MessageStore store;
    @Autowired DataSource dataSource;

    private static MessengerEvent event(String page, String sender, String mid) {
        return new MessengerEvent(page, sender, mid, "Xin chào", Instant.now());
    }

    private int count(String sql, String a, String b) {
        return JdbcClient.create(dataSource).sql(sql).param("a", a).param("b", b)
                .query(Integer.class).single();
    }

    @Test
    void messagesFromSameSenderShareOneConversation() {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();

        assertTrue(store.saveIncoming(event(page, sender, "mid." + UUID.randomUUID())));
        assertTrue(store.saveIncoming(event(page, sender, "mid." + UUID.randomUUID())));

        assertEquals(1, count("SELECT count(*) FROM conversation WHERE page_id = :a AND sender_id = :b", page, sender));
        assertEquals(2, count("SELECT count(*) FROM message m JOIN conversation c ON c.id = m.conversation_id "
                + "WHERE c.page_id = :a AND c.sender_id = :b AND m.direction = 'IN'", page, sender));
    }

    @Test
    void duplicateMidIsStoredOnce() {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();
        String mid = "mid." + UUID.randomUUID();

        assertTrue(store.saveIncoming(event(page, sender, mid)));
        assertFalse(store.saveIncoming(event(page, sender, mid)));

        assertEquals(1, count("SELECT count(*) FROM message m JOIN conversation c ON c.id = m.conversation_id "
                + "WHERE c.page_id = :a AND c.sender_id = :b", page, sender));
    }

    @Test
    void differentSendersGetDifferentConversations() {
        String page = "P-" + UUID.randomUUID();
        String sender1 = "U-" + UUID.randomUUID();
        String sender2 = "U-" + UUID.randomUUID();

        store.saveIncoming(event(page, sender1, "mid." + UUID.randomUUID()));
        store.saveIncoming(event(page, sender2, "mid." + UUID.randomUUID()));

        assertEquals(2, count("SELECT count(*) FROM conversation WHERE page_id = :a AND :b IS NOT NULL", page, sender1));
    }
}
