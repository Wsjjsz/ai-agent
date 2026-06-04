package com.aiagent.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddMessageRequest(
        @Pattern(regexp = "^(user|assistant)?$", message = "role must be user or assistant")
        String role,

        @NotBlank(message = "content cannot be empty")
        @Size(max = 20000, message = "content length must be less than or equal to 20000")
        String content
) {
}
