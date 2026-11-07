package com.fertilizer.backend.llm;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Công cụ mẫu để kiểm chứng AI-02; AI-03 sẽ thêm các công cụ nghiệp vụ thật. */
@Component
public class CurrentTimeTool implements LlmTool {

    @Override
    public String name() {
        return "lay_thoi_gian_hien_tai";
    }

    @Override
    public String description() {
        return "Lấy ngày giờ hiện tại (giờ Việt Nam). Dùng khi khách hỏi hôm nay là ngày nào, "
                + "hoặc cần tính hạn giao hàng, hạn thanh toán.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> args) {
        return ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi")));
    }
}
