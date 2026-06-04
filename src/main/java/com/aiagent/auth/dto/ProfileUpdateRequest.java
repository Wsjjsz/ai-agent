package com.aiagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "nickname cannot be empty")
        @Size(max = 32, message = "nickname length must be less than or equal to 32")
        String nickname,

        @Size(max = 500, message = "avatarUrl length must be less than or equal to 500")
        String avatarUrl
) {
}
