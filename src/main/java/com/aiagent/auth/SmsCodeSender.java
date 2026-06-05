package com.aiagent.auth;

public interface SmsCodeSender {

    String sendLoginCode(String phone, String code);
}
