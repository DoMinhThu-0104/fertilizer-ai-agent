package com.fertilizer.backend.chat;

import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Hàng đợi trả lời nằm trong bảng message (tin IN có reply_status).
 * Lấy việc bằng một câu UPDATE nguyên tử + FOR UPDATE SKIP LOCKED nên nhiều tiến trình
 * cùng chạy cũng không lấy trùng một tin.
 */
@Service
public class ReplyQueue {

    /** Một tin khách đang chờ trả lời. attempts là số lần đã lấy (kể cả lần này). */
    public record ReplyJob(long id, int attempts, MessengerEvent event) {}

    private final JdbcClient jdbc;
    private final int leaseSeconds;

    public ReplyQueue(DataSource dataSource, @Value("${reply.lease-seconds:300}") int leaseSeconds) {
        this.jdbc = JdbcClient.create(dataSource);
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * Lấy tối đa {@code limit} tin đến hạn. Tin được "cho thuê" leaseSeconds giây: nếu tiến trình
     * sập giữa chừng thì hết hạn thuê tin tự quay lại hàng đợi.
     */
    public List<ReplyJob> claim(int limit) {
        return jdbc.sql("""
                UPDATE message m
                SET reply_status = 'PROCESSING',
                    reply_attempts = m.reply_attempts + 1,
                    reply_next_at = now() + make_interval(secs => :lease)
                FROM conversation c
                WHERE c.id = m.conversation_id
                  AND m.id IN (
                      SELECT id FROM message
                      WHERE direction = 'IN'
                        AND reply_status IN ('PENDING', 'PROCESSING')
                        AND reply_next_at <= now()
                      ORDER BY id
                      LIMIT :limit
                      FOR UPDATE SKIP LOCKED)
                RETURNING m.id, m.reply_attempts, m.external_id, m.body, m.sent_at, c.page_id, c.sender_id
                """)
                .param("lease", leaseSeconds)
                .param("limit", limit)
                .query((rs, i) -> new ReplyJob(
                        rs.getLong("id"),
                        rs.getInt("reply_attempts"),
                        new MessengerEvent(
                                rs.getString("page_id"),
                                rs.getString("sender_id"),
                                rs.getString("external_id"),
                                rs.getString("body"),
                                rs.getObject("sent_at", OffsetDateTime.class).toInstant())))
                .list();
    }

    public void markDone(long id) {
        jdbc.sql("UPDATE message SET reply_status = 'DONE', reply_error = NULL WHERE id = :id AND reply_status = 'PROCESSING'")
                .param("id", id)
                .update();
    }

    /** Thử lại sau một lúc, giãn dần: 10s, 20s, 40s, 80s... tối đa 5 phút. */
    public void markRetry(long id, int attempts, String error) {
        long delay = Math.min(300L, 10L << Math.max(0, attempts - 1));
        jdbc.sql("""
                UPDATE message
                SET reply_status = 'PENDING', reply_error = :error,
                    reply_next_at = now() + make_interval(secs => :delay)
                WHERE id = :id AND reply_status = 'PROCESSING'
                """)
                .param("error", shorten(error))
                .param("delay", delay)
                .param("id", id)
                .update();
    }

    public void markFailed(long id, String error) {
        jdbc.sql("UPDATE message SET reply_status = 'FAILED', reply_error = :error WHERE id = :id AND reply_status = 'PROCESSING'")
                .param("error", shorten(error))
                .param("id", id)
                .update();
    }

    /**
     * Dọn hàng đợi: bỏ tin quá cũ (Meta chỉ cho nhắn lại trong 24 giờ sau tin cuối của khách)
     * và tin đã hết lượt thử (ví dụ sập nhiều lần giữa chừng).
     *
     * @return số tin bị dọn
     */
    public int sweep(int maxAttempts, int maxAgeHours) {
        int expired = jdbc.sql("""
                UPDATE message
                SET reply_status = 'EXPIRED', reply_error = 'qua han tra loi'
                WHERE direction = 'IN' AND reply_status IN ('PENDING', 'PROCESSING')
                  AND reply_next_at <= now()
                  AND sent_at < now() - make_interval(hours => :hours)
                """)
                .param("hours", maxAgeHours)
                .update();
        int failed = jdbc.sql("""
                UPDATE message
                SET reply_status = 'FAILED', reply_error = 'het luot thu'
                WHERE direction = 'IN' AND reply_status IN ('PENDING', 'PROCESSING')
                  AND reply_next_at <= now()
                  AND reply_attempts >= :max
                """)
                .param("max", maxAttempts)
                .update();
        return expired + failed;
    }

    private static String shorten(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 100 ? error : error.substring(0, 100);
    }
}
