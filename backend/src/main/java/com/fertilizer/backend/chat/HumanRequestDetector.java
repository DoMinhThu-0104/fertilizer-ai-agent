package com.fertilizer.backend.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Nhận ra khách đang xin gặp người thật. Chạy bằng code, không phụ thuộc AI. */
public final class HumanRequestDetector {

    private static final List<String> PHRASES = List.of(
            "gap nhan vien", "gap nguoi that", "gap nguoi tu van", "gap tu van vien",
            "noi chuyen voi nhan vien", "noi chuyen voi nguoi", "noi chuyen voi tu van vien",
            "chuyen nhan vien", "chuyen cho nhan vien", "can nhan vien", "can nguoi ho tro",
            "nhan vien tu van", "tu van vien", "cho xin nhan vien", "goi nhan vien");

    private HumanRequestDetector() {}

    public static boolean asksForHuman(String text) {
        if (text == null) {
            return false;
        }
        String plain = fold(text);
        return PHRASES.stream().anyMatch(plain::contains);
    }

    /** Bỏ dấu tiếng Việt, đổi về chữ thường. */
    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT);
    }
}
