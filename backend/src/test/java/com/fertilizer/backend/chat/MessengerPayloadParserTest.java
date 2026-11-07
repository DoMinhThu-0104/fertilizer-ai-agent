package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class MessengerPayloadParserTest {

    private final MessengerPayloadParser parser = new MessengerPayloadParser(JsonMapper.builder().build());

    private static byte[] payload(String eventPart) {
        String json = "{\"object\":\"page\",\"entry\":[{\"id\":\"P1\",\"time\":1,\"messaging\":["
                + "{\"sender\":{\"id\":\"U1\"},\"recipient\":{\"id\":\"P1\"},\"timestamp\":1730000000000,"
                + eventPart + "}]}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesTextMessage() {
        List<MessengerEvent> events = parser.parse(payload("\"message\":{\"mid\":\"m1\",\"text\":\"Xin chào\"}"));

        assertEquals(1, events.size());
        MessengerEvent e = events.get(0);
        assertEquals("P1", e.pageId());
        assertEquals("U1", e.senderId());
        assertEquals("m1", e.mid());
        assertEquals("Xin chào", e.text());
        assertEquals(Instant.ofEpochMilli(1730000000000L), e.sentAt());
    }

    @Test
    void skipsEchoMessages() {
        assertTrue(parser.parse(payload("\"message\":{\"mid\":\"m2\",\"text\":\"x\",\"is_echo\":true}")).isEmpty());
    }

    @Test
    void skipsDeliveryEvents() {
        assertTrue(parser.parse(payload("\"delivery\":{\"mids\":[\"m1\"],\"watermark\":1}")).isEmpty());
    }

    @Test
    void skipsAttachmentWithoutText() {
        assertTrue(parser.parse(payload("\"message\":{\"mid\":\"m3\",\"attachments\":[{\"type\":\"image\"}]}")).isEmpty());
    }

    @Test
    void invalidOrForeignPayloadGivesNothing() {
        assertTrue(parser.parse("khong phai json".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(parser.parse("{\"object\":\"user\"}".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }
}
