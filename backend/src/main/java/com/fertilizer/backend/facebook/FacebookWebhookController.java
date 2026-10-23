package com.fertilizer.backend.facebook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/facebook")
public class FacebookWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FacebookWebhookController.class);

    private final String appSecret;
    private final String verifyToken;

    public FacebookWebhookController(
            @Value("${facebook.app-secret:}") String appSecret,
            @Value("${facebook.verify-token:}") String verifyToken) {
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!verifyToken.isBlank() && "subscribe".equals(mode)
                && token != null && constantTimeEquals(verifyToken, token)
                && challenge != null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }
        log.warn("Facebook webhook verification rejected");
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] raw,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (!signatureValid(raw, signature)) {
            log.warn("Facebook webhook rejected: invalid signature");
            return ResponseEntity.status(403).build();
        }
        // TODO (sau migration V2): parse JSON, lưu webhook_events (khóa duy nhất theo mid),
        // đưa vào job_queue, rồi trả 200. Không log nội dung tin nhắn.
        return ResponseEntity.ok().build();
    }

    boolean signatureValid(byte[] raw, String header) {
        if (appSecret.isBlank() || header == null || !header.startsWith("sha256=")) {
            return false; // thiếu cấu hình thì từ chối hết (fail closed)
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(raw));
            return constantTimeEquals(expected, header);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}