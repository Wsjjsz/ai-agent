package com.aiagent.history.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Size(max = 100, message = "title length must be less than or equal to 100")
        String title,

        @Pattern(regexp = "^(basic|agent)?$", message = "mode must be basic or agent")
        String mode
) {
}
