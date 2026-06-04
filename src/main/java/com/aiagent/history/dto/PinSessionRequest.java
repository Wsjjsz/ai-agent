package com.aiagent.history.dto;

import jakarta.validation.constraints.NotNull;

public record PinSessionRequest(
        @NotNull(message = "pinned cannot be null")
        Boolean pinned
) {
}
