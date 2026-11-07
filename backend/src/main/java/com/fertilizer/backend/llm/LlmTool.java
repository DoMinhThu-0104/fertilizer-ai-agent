package com.fertilizer.backend.llm;

import java.util.Map;

/**
 * Một công cụ mà LLM có thể yêu cầu gọi. Tham số do LLM sinh ra nên KHÔNG tin cậy:
 * mọi công cụ phải kiểm tra tham số, và không nhận định danh khách từ tham số này.
 */
public interface LlmTool {

    /** Tên công cụ (chữ thường, số, dấu gạch dưới). */
    String name();

    /** Mô tả để LLM biết khi nào nên gọi. */
    String description();

    /** JSON Schema của tham số, ví dụ {"type":"object","properties":{...}}. */
    Map<String, Object> parameters();

    /** Chạy công cụ, trả kết quả dạng chuỗi gửi lại cho LLM. */
    String execute(Map<String, Object> args);
}
