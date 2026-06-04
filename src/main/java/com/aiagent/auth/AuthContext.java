package com.aiagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthContext {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private AuthContext() {
    }

    public static AuthenticatedUser requireUser(HttpServletRequest request) {
        Object value = request.getAttribute(CURRENT_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
