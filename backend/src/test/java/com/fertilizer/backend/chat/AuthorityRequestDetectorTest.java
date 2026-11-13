package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AuthorityRequestDetectorTest {

    @Test
    void detectsClearOverAuthorityRequests() {
        assertEquals(Optional.of("HUY_DON"), AuthorityRequestDetector.reasonFor("Mình muốn hủy đơn hàng này"));
        assertEquals(Optional.of("HOAN_TIEN"), AuthorityRequestDetector.reasonFor("Hoàn tiền giúp mình"));
        assertEquals(Optional.of("KHIEU_NAI"), AuthorityRequestDetector.reasonFor("Tôi muốn khiếu nại"));
        assertEquals(Optional.of("GIAM_GIA"), AuthorityRequestDetector.reasonFor("Đơn 50 bao giảm 20% được không"));
        assertEquals(Optional.of("GIAM_GIA"), AuthorityRequestDetector.reasonFor("bớt 50k cho mình nha"));
    }

    @Test
    void cancellationWinsWhenDiscountAndCancelAppearTogether() {
        assertEquals(Optional.of("HUY_DON"), AuthorityRequestDetector.reasonFor(
                "Mình muốn được giảm giá 20% cho đơn 50 bao, nếu không mình hủy đơn"));
    }

    @Test
    void ignoresOrdinaryQuestions() {
        assertTrue(AuthorityRequestDetector.reasonFor("Hiện có giảm giá không?").isEmpty());
        assertTrue(AuthorityRequestDetector.reasonFor("Hàng giá rẻ nhất là loại nào?").isEmpty());
        assertTrue(AuthorityRequestDetector.reasonFor("Giá phân DAP bao nhiêu?").isEmpty());
        assertTrue(AuthorityRequestDetector.reasonFor(null).isEmpty());
    }
}
