package com.aiagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SmsLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Size(max = 32, message = "手机号长度不能超过 32 位")
        String phone,

        @Size(max = 8, message = "区号长度不能超过 8 位")
        String countryCode,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码必须是 6 位数字")
        String code,

        @Size(max = 128, message = "guestId length must be less than or equal to 128")
        String guestId
) {
}
