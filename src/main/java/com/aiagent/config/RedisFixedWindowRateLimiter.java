package com.aiagent.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisFixedWindowRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public boolean allow(String key, int limit, Duration window) {
        if (redisTemplate == null || limit <= 0) {
            return true;
        }
        try {
            long bucket = System.currentTimeMillis() / window.toMillis();
            String redisKey = "rate_limit:v1:" + key + ":" + bucket;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, window.plusSeconds(5));
            }
            return count == null || count <= limit;
        } catch (Exception ignored) {
            return true;
        }
    }
}
