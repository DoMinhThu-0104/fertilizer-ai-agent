package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private final HandoffService handoff = mock(HandoffService.class);
    private final ReplyService service = new ReplyService(agent, sender, store, handoff);

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
    void sendFailureOfTheFirstPartIsPropagatedSoTheWorkerCanRetry() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(agent.reply(anyString())).thenReturn(new LlmClient.Reply("Xin chào", "test"));
        when(sender.send(anyString(), anyString())).thenThrow(new MessengerException("Send API trả lỗi HTTP 400"));

        assertThrows(MessengerException.class, () -> service.reply(event));

        verify(store, never()).saveOutgoing(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void failureAfterTheFirstPartDoesNotRetryTheWholeReply() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(agent.reply(anyString())).thenReturn(new LlmClient.Reply("x".repeat(4000), "test")); // 3 phần
        when(sender.send(anyString(), anyString()))
                .thenReturn("mid.p1")
                .thenThrow(new MessengerException("Send API trả lỗi HTTP 500"));

        service.reply(event); // không ném: khách đã nhận phần đầu, thử lại cả câu sẽ gửi trùng

        verify(store).saveOutgoing(eq("P1"), eq("U1"), eq("mid.p1"), anyString());
    }

    @Test
    void customerAskingForHumanIsHandedOffWithoutCallingAgent() {
        MessengerEvent ask = new MessengerEvent("P1", "U1", "mid.in2", "Cho mình gặp nhân viên nhé", Instant.now());
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(sender.send(eq("U1"), anyString())).thenReturn("mid.out3");

        service.reply(ask);

        verify(handoff).toHuman("P1", "U1", "KHACH_YEU_CAU");
        verify(agent, never()).reply(anyString());
        verify(store).saveOutgoing(eq("P1"), eq("U1"), eq("mid.out3"), argThat(s -> s.contains("nhân viên")));
    }

    @Test
    void aiHandoffStillSendsTheFinalAnswerToCustomer() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false, true);
        when(agent.reply(anyString())).thenAnswer(inv -> {
            ConversationContext.current().orElseThrow().markHandedOff(); // như công cụ chuyen_nhan_vien
            return new LlmClient.Reply("Mình sẽ chuyển nhân viên hỗ trợ bạn.", "test");
        });
        when(sender.send(anyString(), anyString())).thenReturn("mid.out4");

        service.reply(event);

        verify(sender).send("U1", "Mình sẽ chuyển nhân viên hỗ trợ bạn.");
    }

    @Test
    void staffTakingOverWhileAiComposesSuppressesTheReply() {
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false, true);
        when(agent.reply(anyString())).thenReturn(new LlmClient.Reply("Câu trả lời của AI", "test"));

        service.reply(event);

        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void overAuthorityRequestIsHandedOffByCode() {
        MessengerEvent cancel = new MessengerEvent("P1", "U1", "mid.in5", "Mình muốn hủy đơn hàng này", Instant.now());
        when(store.isHandedToHuman("P1", "U1")).thenReturn(false);
        when(sender.send(eq("U1"), anyString())).thenReturn("mid.out5");

        service.reply(cancel);

        verify(handoff).toHuman("P1", "U1", "HUY_DON");
        verify(agent, never()).reply(anyString());
    }
}
