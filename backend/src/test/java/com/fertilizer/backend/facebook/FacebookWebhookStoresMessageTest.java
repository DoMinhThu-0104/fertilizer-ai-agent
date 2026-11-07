package com.fertilizer.backend.facebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "facebook.app-secret=test-secret",
        "facebook.verify-token=test-verify"
})
class FacebookWebhookStoresMessageTest {

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signedWebhookStoresTheMessage() throws Exception {
        String page = "P-" + UUID.randomUUID();
        String sender = "U-" + UUID.randomUUID();
        String mid = "mid." + UUID.randomUUID();
        String body = "{\"object\":\"page\",\"entry\":[{\"id\":\"" + page + "\",\"time\":1,\"messaging\":["
                + "{\"sender\":{\"id\":\"" + sender + "\"},\"recipient\":{\"id\":\"" + page + "\"},"
                + "\"timestamp\":1730000000000,\"message\":{\"mid\":\"" + mid + "\",\"text\":\"Xin chào\"}}]}]}";

        mvc.perform(post("/webhooks/facebook")
                .header("X-Hub-Signature-256", sign(body))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        int stored = JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM message WHERE external_id = :mid")
                .param("mid", mid).query(Integer.class).single();
        assertEquals(1, stored);
    }
}
