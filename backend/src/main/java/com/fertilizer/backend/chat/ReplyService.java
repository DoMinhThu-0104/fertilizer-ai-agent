package com.fertilizer.backend.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fertilizer.backend.llm.AgentService;
import com.fertilizer.backend.llm.LlmClient;

/** Nhờ AI soạn câu trả lời rồi gửi cho khách và lưu tin gửi đi. Chạy nền, không log nội dung tin. */
@Service
public class ReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyService.class);

    static final String FALLBACK =
            "Xin lỗi bạn, hệ thống đang bận. Mình sẽ nhờ nhân viên hỗ trợ bạn sớm nhất có thể.";

    private final AgentService agent;
    private final MessengerSender sender;
    private final MessageStore store;

    public ReplyService(AgentService agent, MessengerSender sender, MessageStore store) {
        this.agent = agent;
        this.sender = sender;
        this.store = store;
    }

    @Async
    public void reply(MessengerEvent event) {
        try {
            if (store.isHandedToHuman(event.pageId(), event.senderId())) {
                return; // nhân viên đang xử lý, AI không chen vào (C-05)
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
            for (String part : MessengerSender.split(answer)) {
                String mid = sender.send(event.senderId(), part);
                store.saveOutgoing(event.pageId(), event.senderId(), mid, part);
            }
        } catch (RuntimeException e) {
            log.warn("Không gửi được trả lời: {}", e.getMessage());
        }
    }
}
