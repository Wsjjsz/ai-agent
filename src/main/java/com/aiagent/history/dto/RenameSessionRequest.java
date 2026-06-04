package com.aiagent.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSessionRequest(
        @NotBlank(message = "title cannot be empty")
        @Size(max = 100, message = "title length must be less than or equal to 100")
        String title
) {
}
