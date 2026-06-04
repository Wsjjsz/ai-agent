package com.aiagent.auth;

public record AuthenticatedUser(
        Long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
