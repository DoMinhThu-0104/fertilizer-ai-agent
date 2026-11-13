package com.fertilizer.backend.chat;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API cho nhân viên: hàng chờ, lịch sử hội thoại, trả hội thoại về cho AI. */
@RestController
@RequestMapping("/api")
public class HandoffController {

    private final HandoffService handoff;

    public HandoffController(HandoffService handoff) {
        this.handoff = handoff;
    }

    @GetMapping("/handoffs")
    public List<HandoffService.HandoffItem> waiting() {
        return handoff.waiting();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<HandoffService.MessageItem> messages(@PathVariable long id) {
        return handoff.history(id);
    }

    @PostMapping("/handoffs/{id}/return-to-ai")
    public ResponseEntity<Void> returnToAi(@PathVariable long id) {
        return handoff.toAi(id) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
