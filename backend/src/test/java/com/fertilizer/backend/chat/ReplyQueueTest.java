package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Cần PostgreSQL đang chạy. Dùng id ngẫu nhiên; nên tắt backend khi chạy để worker không lấy tin thử. */
@SpringBootTest
class ReplyQueueTest {

    @Autowired ReplyQueue queue;
    @Autowired MessageStore store;
    @Autowired DataSource dataSource;

    private String save(Instant sentAt) {
        String mid = "mid." + UUID.randomUUID();
        store.saveIncoming(new MessengerEvent("P-" + UUID.randomUUID(), "U-" + UUID.randomUUID(), mid, "Xin chào", sentAt));
        return mid;
    }

    private String status(String mid) {
        return JdbcClient.create(dataSource).sql("SELECT reply_status FROM message WHERE external_id = :m")
                .param("m", mid).query(String.class).single();
    }

    private void makeDueNow(String mid) {
        JdbcClient.create(dataSource).sql("UPDATE message SET reply_next_at = now() WHERE external_id = :m")
                .param("m", mid).update();
    }

    private ReplyQueue.ReplyJob claimMine(String mid) {
        return queue.claim(500).stream().filter(j -> mid.equals(j.event().mid())).findFirst().orElse(null);
    }

    @Test
    void newIncomingMessageIsPending() {
        assertEquals("PENDING", status(save(Instant.now())));
    }

    @Test
    void claimLeasesTheMessageSoNobodyElseTakesIt() {
        String mid = save(Instant.now());

        ReplyQueue.ReplyJob job = claimMine(mid);

        assertNotNull(job);
        assertEquals(1, job.attempts());
        assertEquals("Xin chào", job.event().text());
        assertEquals("PROCESSING", status(mid));
        assertNull(claimMine(mid)); // đang được thuê, chưa đến hạn lấy lại
    }

    @Test
    void retryComesBackLaterAndDoneIsFinal() {
        String mid = save(Instant.now());
        ReplyQueue.ReplyJob first = claimMine(mid);

        queue.markRetry(first.id(), first.attempts(), "loi tam");
        assertEquals("PENDING", status(mid));
        assertNull(claimMine(mid)); // phải chờ giãn cách

        makeDueNow(mid);
        ReplyQueue.ReplyJob second = claimMine(mid);
        assertEquals(2, second.attempts());

        queue.markDone(second.id());
        assertEquals("DONE", status(mid));
        queue.markFailed(second.id(), "khong duoc ghi de"); // đã DONE thì không đổi
        assertEquals("DONE", status(mid));
    }

    @Test
    void sweepExpiresMessagesOlderThanTheWindow() {
        String old = save(Instant.now().minusSeconds(30 * 3600));
        String fresh = save(Instant.now());

        assertTrue(queue.sweep(5, 20) >= 1);

        assertEquals("EXPIRED", status(old));
        assertEquals("PENDING", status(fresh));
        assertNull(claimMine(old));
    }

    @Test
    void sweepFailsMessagesThatRanOutOfAttempts() {
        String mid = save(Instant.now());
        JdbcClient.create(dataSource)
                .sql("UPDATE message SET reply_attempts = 5, reply_next_at = now() WHERE external_id = :m")
                .param("m", mid).update();

        queue.sweep(5, 20);

        assertEquals("FAILED", status(mid));
    }
}
