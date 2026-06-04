package com.aiagent.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class FileAccessTokenService {

    private final byte[] secret;
    private final long ttlSeconds;

    public FileAccessTokenService(
            @Value("${app.file-access-token-secret:${app.auth.jwt-secret}}") String secret,
            @Value("${app.file-access-token-ttl-seconds:300}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String create(long userId, String path, String disposition) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + "." + expiresAt + "." + normalizeDisposition(disposition) + "." + encode(path);
        return payload + "." + sign(payload);
    }

    public FileAccessGrant verify(String token, String expectedPath, String expectedDisposition) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing file token");
        }
        String[] parts = token.split("\\.", 5);
        if (parts.length != 5) {
            throw new IllegalArgumentException("invalid file token");
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[4].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("invalid file token");
        }
        long expiresAt = Long.parseLong(parts[1]);
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("file token expired");
        }
        String path = decode(parts[3]);
        String disposition = normalizeDisposition(parts[2]);
        if (!MessageDigest.isEqual(path.getBytes(StandardCharsets.UTF_8), expectedPath.getBytes(StandardCharsets.UTF_8))
                || !disposition.equals(normalizeDisposition(expectedDisposition))) {
            throw new IllegalArgumentException("file token scope mismatch");
        }
        return new FileAccessGrant(Long.parseLong(parts[0]), path, disposition);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return encodeBytes(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign file token", e);
        }
    }

    private String normalizeDisposition(String disposition) {
        return "download".equalsIgnoreCase(disposition) ? "download" : "preview";
    }

    private String encode(String value) {
        return encodeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeBytes(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record FileAccessGrant(long userId, String path, String disposition) {
    }
}
