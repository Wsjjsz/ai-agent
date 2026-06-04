package com.aiagent.config;

import com.aiagent.auth.AuthContext;
import com.aiagent.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisFixedWindowRateLimiter rateLimiter;

    @Value("${app.rate-limit.enabled:false}")
    private boolean enabled;

    @Value("${app.rate-limit.default-capacity:120}")
    private int defaultCapacity;

    @Value("${app.rate-limit.ai-capacity:20}")
    private int aiCapacity;

    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    public RateLimitFilter(RedisFixedWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        int capacity = isAiRequest(request) ? aiCapacity : defaultCapacity;
        String route = normalizeRoute(request);
        String ip = resolveClientIp(request);
        String user = resolveUserKey(request);

        boolean allowed = rateLimiter.allow("ip:" + ip + ":route:" + route, capacity, Duration.ofMinutes(1))
                && rateLimiter.allow("user:" + user + ":route:" + route, capacity, Duration.ofMinutes(1))
                && rateLimiter.allow("route:" + route, Math.max(capacity * 20, capacity), Duration.ofMinutes(1));

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\",\"status\":429}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.contains("/health")
                || path.contains("/actuator")
                || path.contains("/swagger")
                || path.contains("/v3/api-docs");
    }

    private boolean isAiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/ai/finance_app/chat") || path.contains("/ai/manus/chat");
    }

    private String normalizeRoute(HttpServletRequest request) {
        return request.getMethod() + ":" + request.getRequestURI()
                .replaceAll("/[0-9a-fA-F-]{16,}", "/{id}")
                .replaceAll("/\\d+", "/{id}");
    }

    private String resolveUserKey(HttpServletRequest request) {
        Object value = request.getAttribute(AuthContext.CURRENT_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return String.valueOf(user.id());
        }
        String guestId = request.getHeader("X-Guest-Id");
        if (guestId != null && !guestId.isBlank()) {
            return "guest:" + guestId.replaceAll("[^a-zA-Z0-9_-]", "");
        }
        return "anonymous:" + resolveClientIp(request);
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
