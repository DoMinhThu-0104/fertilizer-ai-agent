package com.fertilizer.backend.chat;

import java.time.Instant;

/** Một tin nhắn văn bản khách gửi đến Fanpage. */
public record MessengerEvent(String pageId, String senderId, String mid, String text, Instant sentAt) {}
