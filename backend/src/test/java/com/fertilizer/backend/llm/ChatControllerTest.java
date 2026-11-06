package com.fertilizer.backend.llm;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean LlmClient llm;

    @Test
    void withoutLoginIsUnauthorized() throws Exception {
        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void returnsReplyFromLlm() throws Exception {
        when(llm.chat(anyString(), anyString())).thenReturn(new LlmClient.Reply("Chào bạn", "test-model"));

        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Chào bạn"))
            .andExpect(jsonPath("$.model").value("test-model"));
    }

    @Test
    @WithMockUser
    void blankMessageIsBadRequest() throws Exception {
        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void llmFailureReturns503() throws Exception {
        when(llm.chat(anyString(), anyString())).thenThrow(new LlmException("hết model"));

        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isServiceUnavailable());
    }
}
