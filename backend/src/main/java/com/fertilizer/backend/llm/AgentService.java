package com.fertilizer.backend.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Agent trả lời khách: gắn system prompt (chính sách phạm vi) vào mọi lượt gọi LLM. */
@Service
public class AgentService {

    private final LlmClient llm;
    private final String systemPrompt;

    public AgentService(LlmClient llm,
                        @Value("classpath:prompts/agent-system.txt") Resource promptFile) {
        this.llm = llm;
        try {
            this.systemPrompt = promptFile.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được system prompt", e);
        }
    }

    public LlmClient.Reply reply(String userMessage) {
        return llm.chat(systemPrompt, userMessage);
    }
}
