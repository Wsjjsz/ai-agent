package com.aiagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmsCodeSendRequest(
        @NotBlank(message = "手机号不能为空")
        @Size(max = 32, message = "手机号长度不能超过 32 位")
        String phone,

        @Size(max = 8, message = "区号长度不能超过 8 位")
        String countryCode
) {
}
