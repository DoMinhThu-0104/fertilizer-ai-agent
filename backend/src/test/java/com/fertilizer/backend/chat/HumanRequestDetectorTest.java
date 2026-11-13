package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HumanRequestDetectorTest {

    @Test
    void detectsRequestsWithAndWithoutDiacritics() {
        assertTrue(HumanRequestDetector.asksForHuman("Cho mình gặp nhân viên"));
        assertTrue(HumanRequestDetector.asksForHuman("cho minh gap nhan vien di"));
        assertTrue(HumanRequestDetector.asksForHuman("Mình muốn nói chuyện với người thật"));
        assertTrue(HumanRequestDetector.asksForHuman("CHUYỂN NHÂN VIÊN GIÚP MÌNH"));
    }

    @Test
    void ignoresNormalQuestions() {
        assertFalse(HumanRequestDetector.asksForHuman("Giá phân DAP bao nhiêu?"));
        assertFalse(HumanRequestDetector.asksForHuman("Nhân viên giao hàng đến lúc nào?"));
        assertFalse(HumanRequestDetector.asksForHuman(null));
        assertFalse(HumanRequestDetector.asksForHuman(""));
    }
}
