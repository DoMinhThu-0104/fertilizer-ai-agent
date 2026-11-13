package com.fertilizer.backend.chat;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fertilizer.backend.llm.LlmTool;

/** Cho AI chuyển hội thoại hiện tại cho nhân viên. Hội thoại lấy từ ngữ cảnh, không từ tham số. */
@Component
public class HandoffTool implements LlmTool {

    private final HandoffService handoff;

    public HandoffTool(HandoffService handoff) {
        this.handoff = handoff;
    }

    @Override
    public String name() {
        return "chuyen_nhan_vien";
    }

    @Override
    public String description() {
        return "Chuyển cuộc trò chuyện hiện tại cho nhân viên khi khách cần điều AI không có quyền "
                + "quyết định (giảm giá, hủy đơn, hoàn tiền, khiếu nại) hoặc khi AI không thể trả lời chắc chắn. "
                + "Sau khi gọi, hãy báo khách ngắn gọn rằng nhân viên sẽ hỗ trợ sớm.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "ly_do", Map.of(
                                "type", "string",
                                "enum", List.of("GIAM_GIA", "HUY_DON", "HOAN_TIEN", "KHIEU_NAI", "KHAC"))),
                "required", List.of("ly_do"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        var context = ConversationContext.current();
        if (context.isEmpty()) {
            return "Không có hội thoại nào để chuyển.";
        }
        ConversationContext ctx = context.get();
        Object raw = args == null ? null : args.get("ly_do");
        String reason = HandoffService.normalizeReason(raw == null ? null : raw.toString());
        handoff.toHuman(ctx.pageId(), ctx.senderId(), reason);
        ctx.markHandedOff();
        return "Đã chuyển cho nhân viên. Hãy báo khách ngắn gọn rằng nhân viên sẽ hỗ trợ sớm.";
    }
}
