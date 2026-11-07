package com.fertilizer.backend.chat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fertilizer.backend.llm.AgentService;
import com.fertilizer.backend.llm.LlmClient;

class ReplyServiceTest {

    private final AgentService agent = mock(AgentService.class);
    private final MessengerSender sender = mock(MessengerSender.class);
    private final MessageStore store = mock(MessageStore.class);
    private final ReplyService service = new ReplyService(agent, sender, store);

    private final MessengerEvent event = new MessengerEvent("P1", "U1", "mid.in1", "Giá phân DAP?", Instant.now());

    @Test
    void repliesAndStoresOutgoingMessage() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(agent.reply("Giá phân DAP?")).thenReturn(new LlmClient.Reply("Bạn cho mình xin loại và số lượng nhé.", "test"));
        when(sender.send("U1", "Bạn cho mình xin loại và số lượng nhé.")).thenReturn("mid.out1");

        service.reply(event);

        verify(store).saveOutgoing("P1", "U1", "mid.out1", "Bạn cho mình xin loại và số lượng nhé.");
    }

    @Test
    void doesNotReplyWhenConversationIsHandedToHuman() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(true);

        service.reply(event);

        verify(agent, never()).reply(anyString());
        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void sendsFallbackWhenAgentFails() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(agent.reply(anyString())).thenThrow(new RuntimeException("llm down"));
        when(sender.send(eq("U1"), argThat(s -> s.contains("nhân viên")))).thenReturn("mid.out2");

        service.reply(event);

        verify(store).saveOutgoing(eq("P1"), eq("U1"), eq("mid.out2"), argThat(s -> s.contains("nhân viên")));
    }

    @Test
    void sendFailureDoesNotThrow() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(agent.reply(anyString())).thenReturn(new LlmClient.Reply("Xin chào", "test"));
        when(sender.send(anyString(), anyString())).thenThrow(new MessengerException("Send API trả lỗi HTTP 400"));

        service.reply(event); // không được ném lỗi ra ngoài luồng nền

        verify(store, never()).saveOutgoing(anyString(), anyString(), anyString(), anyString());
    }
}
