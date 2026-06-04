package com.aiagent.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public JwtAuthFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        }
        if (token == null || token.isBlank()) {
            String guestId = request.getHeader("X-Guest-Id");
            if (guestId == null || guestId.isBlank()) {
                unauthorized(response);
                return;
            }
            try {
                request.setAttribute(AuthContext.CURRENT_USER_ATTRIBUTE, userRepository.findOrCreateGuest(guestId));
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                unauthorized(response);
            }
            return;
        }

        var user = tokenService.parseUserId(token).flatMap(userRepository::findAuthenticatedById);
        if (user.isEmpty()) {
            unauthorized(response);
            return;
        }

        request.setAttribute(AuthContext.CURRENT_USER_ATTRIBUTE, user.get());
        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ((path.contains("/ai/manus/file/preview") || path.contains("/ai/manus/file/download"))
                && request.getParameter("file_token") != null) {
            return true;
        }
        return path.contains("/auth/login")
                || path.contains("/auth/sms/")
                || path.contains("/auth/avatar/file/")
                || path.contains("/hotnews/list")
                || path.contains("/hotnews/image")
                || path.contains("/health")
                || path.contains("/actuator")
                || path.contains("/swagger")
                || path.contains("/v3/api-docs");
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"请先登录\",\"status\":401}");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
