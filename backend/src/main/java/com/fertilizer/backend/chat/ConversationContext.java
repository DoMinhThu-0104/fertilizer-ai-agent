package com.fertilizer.backend.chat;

import java.util.Optional;

/**
 * Hội thoại đang được xử lý trên luồng hiện tại. Do hệ thống đặt, công cụ của AI đọc từ đây
 * thay vì tin vào tham số AI đưa ra.
 */
public final class ConversationContext {

    private static final ThreadLocal<ConversationContext> CURRENT = new ThreadLocal<>();

    private final String pageId;
    private final String senderId;
    private boolean handedOff;

    private ConversationContext(String pageId, String senderId) {
        this.pageId = pageId;
        this.senderId = senderId;
    }

    public static ConversationContext open(String pageId, String senderId) {
        ConversationContext ctx = new ConversationContext(pageId, senderId);
        CURRENT.set(ctx);
        return ctx;
    }

    public static Optional<ConversationContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void close() {
        CURRENT.remove();
    }

    public String pageId() { return pageId; }

    public String senderId() { return senderId; }

    public boolean handedOff() { return handedOff; }

    public void markHandedOff() { this.handedOff = true; }
}
