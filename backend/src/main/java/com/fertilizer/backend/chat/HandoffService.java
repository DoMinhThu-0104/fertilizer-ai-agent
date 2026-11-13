package com.fertilizer.backend.chat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Chuyển hội thoại giữa AI và nhân viên. */
@Service
public class HandoffService {

    public static final Set<String> REASONS =
            Set.of("KHACH_YEU_CAU", "GIAM_GIA", "HUY_DON", "HOAN_TIEN", "KHIEU_NAI", "KHAC");

    public record HandoffItem(long id, String pageId, String senderId, String reason,
                              OffsetDateTime handoffAt, OffsetDateTime lastMessageAt) {}

    public record MessageItem(String direction, String body, OffsetDateTime sentAt) {}

    private final JdbcClient jdbc;

    public HandoffService(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Lý do lạ (do AI tự bịa) thì quy về KHAC. */
    public static String normalizeReason(String reason) {
        return reason != null && REASONS.contains(reason) ? reason : "KHAC";
    }

    /** @return true nếu vừa chuyển từ AI sang NGUOI; false nếu đã ở NGUOI hoặc không có hội thoại */
    public boolean toHuman(String pageId, String senderId, String reason) {
        return jdbc.sql("""
                UPDATE conversation
                SET status = 'NGUOI', handoff_reason = :reason, handoff_at = now()
                WHERE page_id = :page AND sender_id = :sender AND status = 'AI'
                """)
                .param("reason", normalizeReason(reason))
                .param("page", pageId)
                .param("sender", senderId)
                .update() == 1;
    }

    /** @return true nếu vừa trả từ NGUOI về AI */
    public boolean toAi(long conversationId) {
        return jdbc.sql("""
                UPDATE conversation
                SET status = 'AI', handoff_reason = NULL, handoff_at = NULL
                WHERE id = :id AND status = 'NGUOI'
                """)
                .param("id", conversationId)
                .update() == 1;
    }

    /** Hội thoại đang chờ nhân viên, cũ nhất trước. */
    public List<HandoffItem> waiting() {
        return jdbc.sql("""
                SELECT id, page_id, sender_id, handoff_reason, handoff_at, last_message_at
                FROM conversation WHERE status = 'NGUOI' ORDER BY handoff_at
                """)
                .query((rs, i) -> new HandoffItem(
                        rs.getLong("id"),
                        rs.getString("page_id"),
                        rs.getString("sender_id"),
                        rs.getString("handoff_reason"),
                        rs.getObject("handoff_at", OffsetDateTime.class),
                        rs.getObject("last_message_at", OffsetDateTime.class)))
                .list();
    }

    /** Tối đa 50 tin gần nhất, cũ trước, để nhân viên đọc ngữ cảnh. */
    public List<MessageItem> history(long conversationId) {
        List<MessageItem> items = new ArrayList<>(jdbc.sql("""
                SELECT direction, body, sent_at FROM message
                WHERE conversation_id = :id ORDER BY sent_at DESC, id DESC LIMIT 50
                """)
                .param("id", conversationId)
                .query((rs, i) -> new MessageItem(
                        rs.getString("direction"),
                        rs.getString("body"),
                        rs.getObject("sent_at", OffsetDateTime.class)))
                .list());
        Collections.reverse(items);
        return items;
    }
}
