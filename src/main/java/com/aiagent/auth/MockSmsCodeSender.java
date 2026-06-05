package com.aiagent.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.auth.sms-provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsCodeSender implements SmsCodeSender {

    @Override
    public String sendLoginCode(String phone, String code) {
        log.info("Mock SMS login code for {}: {}", maskPhone(phone), code);
        return code;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "unknown";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
