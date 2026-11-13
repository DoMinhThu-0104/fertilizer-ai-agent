package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Bảo vệ: test không được tự gọi Gemini hay Meta qua worker. */
@SpringBootTest
class ReplyWorkerDisabledInTestsTest {

    @Autowired ApplicationContext context;

    @Test
    void workerIsNotRunningDuringTests() {
        assertTrue(context.getBeansOfType(ReplyWorker.class).isEmpty());
    }
}
