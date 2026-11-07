package com.fertilizer.backend.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class LlmClientToolTest {

    private static final String URL = "http://llm.test/chat/completions";

    private static final String TOOL_CALL = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":null,\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"xin chao\\\"}\"}}]}}]}";

    private static final String FINAL_ANSWER =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Xong rồi\"}}]}";

    private static final LlmTool ECHO = new LlmTool() {
        @Override public String name() { return "echo"; }
        @Override public String description() { return "Trả lại nội dung"; }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string")));
        }
        @Override public String execute(Map<String, Object> args) { return "echo:" + args.get("text"); }
    };

    private LlmClient client(RestClient.Builder builder, int maxSteps) {
        return new LlmClient(builder.baseUrl("http://llm.test").build(),
                List.of("m1"), maxSteps, JsonMapper.builder().build());
    }

    @Test
    void runsToolThenReturnsFinalAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(request -> assertTrue(
                        ((MockClientHttpRequest) request).getBodyAsString().contains("echo:xin chao")))
                .andRespond(withSuccess(FINAL_ANSWER, MediaType.APPLICATION_JSON));

        LlmClient.Reply reply = client(builder, 3).chat("sys", "hi", List.of(ECHO));

        assertEquals("Xong rồi", reply.text());
        assertEquals("m1", reply.model());
        server.verify();
    }

    @Test
    void stopsWhenToolLoopExceedsMaxSteps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));

        LlmClient llm = client(builder, 2);

        assertThrows(LlmException.class, () -> llm.chat("sys", "hi", List.of(ECHO)));
        server.verify();
    }
}
