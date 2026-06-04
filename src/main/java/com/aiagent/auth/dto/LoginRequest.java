package com.aiagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username cannot be empty")
        @Size(max = 64, message = "username length must be less than or equal to 64")
        String username,

        @Size(max = 8, message = "区号长度不能超过 8 位")
        String countryCode,

        @NotBlank(message = "password cannot be empty")
        @Size(max = 128, message = "password length must be less than or equal to 128")
        String password,

        @Size(max = 128, message = "guestId length must be less than or equal to 128")
        String guestId
) {
}
