package com.aiagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordSetRequest(
        @NotBlank(message = "password cannot be empty")
        @Size(min = 6, max = 64, message = "password length must be 6-64")
        String password
) {
}
