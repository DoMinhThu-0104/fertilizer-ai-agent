package com.fertilizer.backend.chat;

/** Lỗi khi gửi tin qua Messenger. Nội dung lỗi không chứa token hay nội dung tin nhắn. */
public class MessengerException extends RuntimeException {
    public MessengerException(String message) {
        super(message);
    }
}
