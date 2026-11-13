package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HandoffToolTest {

    private final HandoffService handoff = mock(HandoffService.class);
    private final HandoffTool tool = new HandoffTool(handoff);

    @AfterEach
    void cleanUp() {
        ConversationContext.close();
    }

    @Test
    void handsOffTheConversationFromContext() {
        ConversationContext ctx = ConversationContext.open("P1", "U1");

        tool.execute(Map.of("ly_do", "GIAM_GIA"));

        verify(handoff).toHuman("P1", "U1", "GIAM_GIA");
        assertTrue(ctx.handedOff());
    }

    @Test
    void unknownReasonBecomesKhac() {
        ConversationContext.open("P1", "U1");

        tool.execute(Map.of("ly_do", "mot ly do tu bia"));

        verify(handoff).toHuman("P1", "U1", "KHAC");
    }

    @Test
    void ignoresAnyIdentityInArguments() {
        ConversationContext.open("P1", "U1");

        tool.execute(Map.of("ly_do", "HUY_DON", "sender_id", "NGUOI-KHAC"));

        verify(handoff).toHuman("P1", "U1", "HUY_DON"); // luôn là hội thoại hiện tại
    }

    @Test
    void doesNothingWithoutAConversation() {
        tool.execute(Map.of("ly_do", "KHAC"));

        verify(handoff, never()).toHuman(anyString(), anyString(), anyString());
        assertFalse(ConversationContext.current().isPresent());
    }
}
