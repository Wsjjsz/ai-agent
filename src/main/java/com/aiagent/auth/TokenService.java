package com.aiagent.auth;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class TokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    @Value("${app.auth.jwt-secret:ai-agent-local-dev-secret-change-me}")
    private String jwtSecret;

    @Value("${app.auth.token-ttl-seconds:604800}")
    private long tokenTtlSeconds;

    public String createToken(AuthenticatedUser user) {
        long now = Instant.now().getEpochSecond();
        JSONObject header = JSONUtil.createObj()
                .set("alg", "HS256")
                .set("typ", "JWT");
        JSONObject payload = JSONUtil.createObj()
                .set("sub", user.id())
                .set("username", user.username())
                .set("iat", now)
                .set("exp", now + tokenTtlSeconds);
        String signingInput = base64Json(header) + "." + base64Json(payload);
        return signingInput + "." + sign(signingInput);
    }

    public Optional<Long> parseUserId(String token) {
        try {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(signingInput), parts[2])) {
                return Optional.empty();
            }
            String payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject payload = JSONUtil.parseObj(payloadJson);
            long exp = payload.getLong("exp", 0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            Long userId = payload.getLong("sub");
            return userId == null ? Optional.empty() : Optional.of(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String base64Json(JSONObject object) {
        return URL_ENCODER.encodeToString(object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token signing failed", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
