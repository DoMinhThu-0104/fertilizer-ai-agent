package com.fertilizer.backend.facebook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "facebook.app-secret=test-secret",
        "facebook.verify-token=test-verify"
})
class FacebookWebhookControllerTest {

    @Autowired MockMvc mvc;

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verifyWithCorrectTokenReturnsChallenge() throws Exception {
        mvc.perform(get("/webhooks/facebook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "test-verify")
                .param("hub.challenge", "12345"))
            .andExpect(status().isOk())
            .andExpect(content().string("12345"));
    }

    @Test
    void verifyWithWrongTokenIsForbidden() throws Exception {
        mvc.perform(get("/webhooks/facebook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "sai")
                .param("hub.challenge", "12345"))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutSignatureIsForbidden() throws Exception {
        mvc.perform(post("/webhooks/facebook")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithWrongSignatureIsForbidden() throws Exception {
        mvc.perform(post("/webhooks/facebook")
                .header("X-Hub-Signature-256", "sha256=00")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    // Không gửi CSRF token mà vẫn 200 => chứng minh CSRF đã được loại trừ cho webhook
    @Test
    void postWithValidSignatureIsOk() throws Exception {
        String body = "{\"object\":\"page\",\"entry\":[]}";
        mvc.perform(post("/webhooks/facebook")
                .header("X-Hub-Signature-256", sign(body))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    @Test
    void otherEndpointsStillRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }
}