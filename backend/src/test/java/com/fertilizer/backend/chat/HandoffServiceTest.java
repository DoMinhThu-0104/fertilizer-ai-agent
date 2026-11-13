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

/** Cần PostgreSQL đang chạy. Dùng id ngẫu nhiên nên không cần dọn dữ liệu. */
@SpringBootTest
class HandoffServiceTest {

    @Autowired HandoffService handoff;
    @Autowired MessageStore store;
    @Autowired DataSource dataSource;

    private long conversationId(String page, String sender) {
        return JdbcClient.create(dataSource)
                .sql("SELECT id FROM conversation WHERE page_id = :p AND sender_id = :s")
                .param("p", page).param("s", sender).query(Long.class).single();
    }

    private void incoming(String page, String sender, String text, Instant at) {
        store.saveIncoming(new MessengerEvent(page, sender, "mid." + UUID.randomUUID(), text, at));
    }

    @Test
    void toHumanThenBackToAi() {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();
        incoming(page, sender, "Xin chào", Instant.now());
        long id = conversationId(page, sender);

        assertFalse(store.isHandedToHuman(page, sender));
        assertTrue(handoff.toHuman(page, sender, "GIAM_GIA"));
        assertFalse(handoff.toHuman(page, sender, "GIAM_GIA")); // đã ở NGUOI rồi
        assertTrue(store.isHandedToHuman(page, sender));
        assertTrue(handoff.waiting().stream().anyMatch(h -> h.id() == id && "GIAM_GIA".equals(h.reason())));

        assertTrue(handoff.toAi(id));
        assertFalse(store.isHandedToHuman(page, sender));
        assertFalse(handoff.toAi(id)); // đã ở AI rồi
        assertTrue(handoff.waiting().stream().noneMatch(h -> h.id() == id));
    }

    @Test
    void unknownReasonIsStoredAsKhac() {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();
        incoming(page, sender, "Xin chào", Instant.now());
        long id = conversationId(page, sender);

        handoff.toHuman(page, sender, "ly do la");

        assertTrue(handoff.waiting().stream().anyMatch(h -> h.id() == id && "KHAC".equals(h.reason())));
    }

    @Test
    void historyIsOldestFirst() {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();
        Instant now = Instant.now();
        incoming(page, sender, "tin 1", now);
        incoming(page, sender, "tin 2", now.plusSeconds(1));

        var history = handoff.history(conversationId(page, sender));

        assertEquals(2, history.size());
        assertEquals("tin 1", history.get(0).body());
        assertEquals("tin 2", history.get(1).body());
    }

    @Test
    void toHumanWithoutConversationChangesNothing() {
        assertFalse(handoff.toHuman("P-khong-co", "U-khong-co", "KHAC"));
    }
}
