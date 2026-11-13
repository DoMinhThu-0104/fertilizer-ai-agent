package com.fertilizer.backend.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fertilizer.backend.llm.AgentService;
import com.fertilizer.backend.llm.LlmClient;

/**
 * Soạn câu trả lời bằng AI rồi gửi cho khách và lưu tin gửi đi. Được ReplyWorker gọi trên luồng nền.
 * Lỗi gửi ở phần đầu tiên được ném ra để worker thử lại. Không log nội dung tin.
 */
@Service
public class ReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyService.class);

    static final String FALLBACK =
            "Xin lỗi bạn, hệ thống đang bận. Mình sẽ nhờ nhân viên hỗ trợ bạn sớm nhất có thể.";

    static final String HANDOFF_MESSAGE =
            "Mình đã chuyển cuộc trò chuyện cho nhân viên. Nhân viên sẽ liên hệ hỗ trợ bạn sớm nhất có thể.";

    private final AgentService agent;
    private final MessengerSender sender;
    private final MessageStore store;
    private final HandoffService handoff;

    public ReplyService(AgentService agent, MessengerSender sender, MessageStore store, HandoffService handoff) {
        this.agent = agent;
        this.sender = sender;
        this.store = store;
        this.handoff = handoff;
    }

    public void reply(MessengerEvent event) {
        ConversationContext ctx = ConversationContext.open(event.pageId(), event.senderId());
        try {
            if (store.isHandedToHuman(event.pageId(), event.senderId())) {
                return; // nhân viên đang xử lý, AI không chen vào
            }
            if (HumanRequestDetector.asksForHuman(event.text())) {
                handoff.toHuman(event.pageId(), event.senderId(), "KHACH_YEU_CAU");
                send(event, HANDOFF_MESSAGE);
                return;
            }
            String authorityReason = AuthorityRequestDetector.reasonFor(event.text()).orElse(null);
            if (authorityReason != null) {
                handoff.toHuman(event.pageId(), event.senderId(), authorityReason);
                send(event, HANDOFF_MESSAGE);
                return;
            }

            String answer;
            try {
                LlmClient.Reply result = agent.reply(event.text());
                answer = result == null ? null : result.text();
            } catch (RuntimeException e) {
                log.warn("AI trả lời lỗi: {}", e.getClass().getSimpleName());
                answer = FALLBACK;
            }
            if (answer == null || answer.isBlank()) {
                answer = FALLBACK;
            }

            // Nhân viên vừa tiếp nhận trong lúc AI soạn: bỏ câu trả lời.
            // Nếu chính AI vừa chuyển (công cụ) thì vẫn gửi câu báo khách.
            if (!ctx.handedOff() && store.isHandedToHuman(event.pageId(), event.senderId())) {
                return;
            }
            send(event, answer);
        } finally {
            ConversationContext.close();
        }
    }

    /** Gửi từng phần. Lỗi ở phần đầu thì ném ra (worker thử lại); lỗi ở phần sau thì chỉ ghi log. */
    private void send(MessengerEvent event, String text) {
        int sent = 0;
        for (String part : MessengerSender.split(text)) {
            String mid;
            try {
                mid = sender.send(event.senderId(), part);
            } catch (MessengerException e) {
                if (sent == 0) {
                    throw e;
                }
                log.warn("Đã gửi {} phần, phần sau lỗi: {}", sent, e.getMessage());
                return;
            }
            store.saveOutgoing(event.pageId(), event.senderId(), mid, part);
            sent++;
        }
    }
}
