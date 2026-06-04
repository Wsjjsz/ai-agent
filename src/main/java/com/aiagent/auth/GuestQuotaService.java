package com.aiagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class GuestQuotaService {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> CONSUME_QUOTA_SCRIPT = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
              local current = tonumber(redis.call('GET', key) or '0')
              if current >= tonumber(ARGV[1]) then
                return 0
              end
            end
            for i, key in ipairs(KEYS) do
              redis.call('INCR', key)
            end
            return 1
            """, Long.class);

    @Value("${app.guest.free-limit:3}")
    private int freeLimit;

    @Value("${app.guest.quota-store:db}")
    private String quotaStore;

    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Autowired
    public GuestQuotaService(JdbcTemplate jdbcTemplate, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public GuestQuotaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = null;
    }

    @Transactional
    public void consumeIfGuest(AuthenticatedUser user, HttpServletRequest request) {
        if (user == null || user.username() == null || !user.username().startsWith("guest_")) {
            return;
        }
        String guestKey = "guest:" + user.username();
        String ipKey = "ip:" + resolveClientIp(request);
        List<String> keys = List.of(guestKey, ipKey).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (useRedisQuota(keys)) {
            return;
        }
        keys.forEach(this::ensureQuotaRow);
        keys.forEach(this::ensureAvailableForUpdate);
        keys.forEach(this::increment);
    }

    private boolean useRedisQuota(List<String> keys) {
        if (!"redis".equalsIgnoreCase(quotaStore) || redisTemplate == null) {
            return false;
        }
        try {
            List<String> redisKeys = keys.stream().map(key -> "guest_quota:v1:" + key).toList();
            Long consumed = redisTemplate.execute(CONSUME_QUOTA_SCRIPT, redisKeys, String.valueOf(freeLimit));
            if (Long.valueOf(1L).equals(consumed)) {
                return true;
            }
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "免费体验次数已用完，请登录后继续使用");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureQuotaRow(String key) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO guest_usage_quota (quota_key, usage_count, create_time, update_time)
                VALUES (?, 0, ?, ?)
                ON CONFLICT (quota_key) DO NOTHING
                """,
                key,
                now,
                now
        );
    }

    private void ensureAvailableForUpdate(String key) {
        int used = readCountForUpdate(key);
        if (used >= freeLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "免费体验次数已用完，请登录后继续使用");
        }
    }

    private int readCountForUpdate(String key) {
        Integer used = jdbcTemplate.queryForObject(
                "SELECT usage_count FROM guest_usage_quota WHERE quota_key = ? FOR UPDATE",
                Integer.class,
                key
        );
        return used == null ? 0 : used;
    }

    private void increment(String key) {
        jdbcTemplate.update(
                "UPDATE guest_usage_quota SET usage_count = usage_count + 1, update_time = ? WHERE quota_key = ?",
                LocalDateTime.now(),
                key
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
