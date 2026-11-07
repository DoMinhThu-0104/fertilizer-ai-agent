package com.fertilizer.backend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class MessengerSenderTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private MessengerSender sender(MockRestServiceServer[] holder, String token) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.test/v1");
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return new MessengerSender(builder.build(), token, mapper);
    }

    @Test
    void sendsTextAndReturnsMessageId() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        MessengerSender sender = sender(server, "TOKEN");
        server[0].expect(requestTo("https://graph.test/v1/me/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TOKEN"))
                .andExpect(jsonPath("$.recipient.id").value("PSID1"))
                .andExpect(jsonPath("$.messaging_type").value("RESPONSE"))
                .andExpect(jsonPath("$.message.text").value("Xin chào"))
                .andRespond(withSuccess("{\"recipient_id\":\"PSID1\",\"message_id\":\"mid.out1\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("mid.out1", sender.send("PSID1", "Xin chào"));
        server[0].verify();
    }

    @Test
    void metaErrorBecomesMessengerExceptionWithoutLeakingToken() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        MessengerSender sender = sender(server, "SECRET-TOKEN");
        server[0].expect(requestTo("https://graph.test/v1/me/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":{}}"));

        MessengerException e = assertThrows(MessengerException.class, () -> sender.send("PSID1", "x"));
        assertTrue(e.getMessage().contains("400"));
        assertTrue(!e.getMessage().contains("SECRET-TOKEN"));
    }

    @Test
    void missingTokenFailsWithoutCallingMeta() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        MessengerSender sender = sender(server, "");
        assertThrows(MessengerException.class, () -> sender.send("PSID1", "x"));
        server[0].verify(); // không có request nào được gửi đi
    }

    @Test
    void splitsLongTextIntoChunks() {
        List<String> parts = MessengerSender.split("x".repeat(4500));
        assertEquals(3, parts.size());
        assertTrue(parts.stream().allMatch(p -> p.length() <= MessengerSender.MAX_LEN));
        assertEquals(1, MessengerSender.split("Ngắn thôi").size());
        assertTrue(MessengerSender.split("   ").isEmpty());
    }
}
