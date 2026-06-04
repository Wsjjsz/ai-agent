package com.aiagent.auth;

public interface SmsCodeSender {

    void sendLoginCode(String phone, String code);
}
