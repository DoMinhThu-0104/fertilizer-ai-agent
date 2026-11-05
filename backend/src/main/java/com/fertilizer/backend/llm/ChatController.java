package com.fertilizer.backend.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(@NotBlank @Size(max = 4000) String message) {}

    public record ChatResponse(String reply, String model) {}

    private final LlmClient llm;

    public ChatController(LlmClient llm) {
        this.llm = llm;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        try {
            LlmClient.Reply reply = llm.chat(request.message());
            return new ChatResponse(reply.text(), reply.model());
        } catch (LlmException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }
}
