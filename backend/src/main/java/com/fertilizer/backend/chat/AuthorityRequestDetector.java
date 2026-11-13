package com.fertilizer.backend.chat;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nhận ra các yêu cầu rõ ràng nằm ngoài quyền của AI (hủy đơn, hoàn tiền, khiếu nại,
 * đòi giảm giá kèm con số). Chạy bằng code nên không phụ thuộc AI có chịu gọi công cụ hay không.
 * Cố ý chặt để không bắt nhầm các câu hỏi thường như "có giảm giá không?".
 */
public final class AuthorityRequestDetector {

    private record Rule(String reason, List<String> phrases) {}

    private static final List<Rule> RULES = List.of(
            new Rule("KHIEU_NAI", List.of("khieu nai", "bat den", "to cao", "lua dao", "phan nan")),
            new Rule("HOAN_TIEN", List.of("hoan tien", "tra lai tien", "tra hang", "doi tra")),
            new Rule("HUY_DON", List.of("huy don", "huy dat hang")));

    /** Ví dụ: "giảm 20%", "bớt 50k", "chiết khấu 5 phần trăm", "giảm giá 100 nghìn". */
    private static final Pattern DISCOUNT = Pattern.compile(
            "(?:giam|bot|chiet khau)\\s*(?:gia\\s*)?\\d+(?:[.,]\\d+)?\\s*(?:%|phan tram|k\\b|nghin|ngan|trieu)");

    private AuthorityRequestDetector() {}

    public static Optional<String> reasonFor(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String plain = HumanRequestDetector.fold(text);
        for (Rule rule : RULES) {
            if (rule.phrases().stream().anyMatch(plain::contains)) {
                return Optional.of(rule.reason());
            }
        }
        if (DISCOUNT.matcher(plain).find()) {
            return Optional.of("GIAM_GIA");
        }
        return Optional.empty();
    }
}
