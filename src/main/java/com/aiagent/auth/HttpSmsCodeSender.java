package com.aiagent.auth;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.auth.sms-provider", havingValue = "http")
public class HttpSmsCodeSender implements SmsCodeSender {

    private final String endpoint;
    private final String secret;

    public HttpSmsCodeSender(@Value("${app.auth.sms-http.endpoint:}") String endpoint,
                             @Value("${app.auth.sms-http.secret:}") String secret) {
        this.endpoint = endpoint;
        this.secret = secret;
    }

    @Override
    public String sendLoginCode(String phone, String code) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "短信服务未配置");
        }
        Map<String, Object> payload = Map.of(
                "phone", phone,
                "code", code,
                "scene", "login"
        );
        HttpResponse response = HttpUtil.createPost(endpoint)
                .header("X-SMS-Secret", secret == null ? "" : secret)
                .body(JSONUtil.toJsonStr(payload))
                .timeout(5000)
                .execute();
        if (!response.isOk()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信发送失败");
        }
        return code;
    }
}
