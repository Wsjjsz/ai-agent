package com.aiagent.config;

import com.aiagent.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key 认证过滤器
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${app.api-key:}")
    private String configuredApiKey;

    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 如果没有配置 API Key，则跳过认证
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 健康检查接口跳过认证
        String path = request.getRequestURI();
        if (path.contains("/auth/")
                || path.contains("/hotnews/list")
                || path.contains("/hotnews/image")
                || path.contains("/ai/manus/file/preview")
                || path.contains("/ai/manus/file/download")
                || path.contains("/health")
                || path.contains("/actuator")
                || path.contains("/swagger")
                || path.contains("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // JWT/Guest 认证过滤器已经建立了当前用户时，不再要求额外 API Key。
        if (request.getAttribute(AuthContext.CURRENT_USER_ATTRIBUTE) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 用户登录态走 JWT 过滤器；API Key 只作为内部调用的可选保护层。
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 验证 API Key
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (configuredApiKey.equals(apiKey)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Invalid or missing API Key\",\"status\":401}");
        }
    }
}
