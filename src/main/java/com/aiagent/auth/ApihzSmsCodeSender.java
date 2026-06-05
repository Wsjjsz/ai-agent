package com.aiagent.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiagent.config.UrlSafety;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.auth.sms-provider", havingValue = "apihz")
public class ApihzSmsCodeSender implements SmsCodeSender {

    private final String endpoint;
    private final String apiId;
    private final String apiKey;
    private final String smsApiId;
    private final String template;
    private final String idParam;
    private final String keyParam;
    private final String phoneParam;
    private final String contentParam;
    private final String apiIdParam;
    private final int timeoutMs;

    public ApihzSmsCodeSender(
            @Value("${app.auth.sms-apihz.endpoint:}") String endpoint,
            @Value("${apihz.id:}") String apiId,
            @Value("${apihz.key:}") String apiKey,
            @Value("${app.auth.sms-apihz.api-id:129}") String smsApiId,
            @Value("${app.auth.sms-apihz.template:}") String template,
            @Value("${app.auth.sms-apihz.id-param:id}") String idParam,
            @Value("${app.auth.sms-apihz.key-param:key}") String keyParam,
            @Value("${app.auth.sms-apihz.phone-param:phone}") String phoneParam,
            @Value("${app.auth.sms-apihz.content-param:content}") String contentParam,
            @Value("${app.auth.sms-apihz.api-id-param:apiid}") String apiIdParam,
            @Value("${app.auth.sms-apihz.timeout-ms:8000}") int timeoutMs) {
        this.endpoint = endpoint;
        this.apiId = apiId;
        this.apiKey = apiKey;
        this.smsApiId = smsApiId;
        this.template = template;
        this.idParam = idParam;
        this.keyParam = keyParam;
        this.phoneParam = phoneParam;
        this.contentParam = contentParam;
        this.apiIdParam = apiIdParam;
        this.timeoutMs = Math.max(timeoutMs, 1000);
    }

    @Override
    public String sendLoginCode(String phone, String code) {
        if (StrUtil.isBlank(endpoint) || StrUtil.isBlank(apiId) || StrUtil.isBlank(apiKey)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "短信服务未配置");
        }
        String targetPhone = normalizeChinaPhone(phone);
        String content = buildContent(code);
        Map<String, Object> form = new LinkedHashMap<>();
        putIfPresent(form, idParam, apiId);
        putIfPresent(form, keyParam, apiKey);
        putIfPresent(form, phoneParam, targetPhone);
        putIfPresent(form, contentParam, content);
        putIfPresent(form, apiIdParam, smsApiId);

        try {
            URI safeEndpoint = UrlSafety.requireSafeHttpUrl(endpoint);
            HttpResponse response = HttpUtil.createPost(safeEndpoint.toString())
                    .form(form)
                    .timeout(timeoutMs)
                    .execute();
            String body = response.body();
            if (!response.isOk()) {
                log.warn("APIHZ SMS HTTP failure, status={}, phone={}, body={}",
                        response.getStatus(), maskPhone(targetPhone), sanitizeProviderBody(body, 200));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信发送失败，请稍后再试");
            }
            if (!isSuccess(body)) {
                log.warn("APIHZ SMS provider failure, phone={}, body={}", maskPhone(targetPhone), sanitizeProviderBody(body, 300));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, providerErrorMessage(body));
            }
            return StrUtil.blankToDefault(extractProviderCode(body), code);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("APIHZ SMS send failed, phone={}, error={}", maskPhone(targetPhone), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信发送失败，请稍后再试");
        }
    }

    private String normalizeChinaPhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的中国大陆手机号");
        }
        return normalized;
    }

    private String buildContent(String code) {
        String contentTemplate = StrUtil.blankToDefault(template, "您的登录验证码是 {code}，5分钟内有效，请勿泄露给他人。");
        return contentTemplate.replace("{code}", code);
    }

    private void putIfPresent(Map<String, Object> form, String key, String value) {
        if (StrUtil.isNotBlank(key) && StrUtil.isNotBlank(value)) {
            form.put(key.trim(), value);
        }
    }

    private boolean isSuccess(String body) {
        if (StrUtil.isBlank(body)) {
            return false;
        }
        String compact = body.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (compact.matches(".*[\"']?code[\"']?[:=]200.*")
                || compact.matches(".*[\"']?status[\"']?[:=]200.*")) {
            return true;
        }
        try {
            Object parsed = JSONUtil.parse(body);
            if (parsed instanceof JSONObject json) {
                String code = firstNonBlank(json.getStr("code"), json.getStr("status"));
                if (StrUtil.isNotBlank(code)) {
                    return "200".equals(code) || "1".equals(code) || "0".equals(code)
                            || "success".equalsIgnoreCase(code) || "ok".equalsIgnoreCase(code);
                }
                String success = json.getStr("success");
                if (StrUtil.isNotBlank(success)) {
                    return "true".equalsIgnoreCase(success) || "1".equals(success);
                }
            }
        } catch (Exception ignored) {
        }
        String normalized = body.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("1") || normalized.contains("success") || normalized.contains("ok")
                || normalized.contains("成功")) {
            return true;
        }
        return false;
    }

    private String providerErrorMessage(String body) {
        try {
            Object parsed = JSONUtil.parse(body);
            if (parsed instanceof JSONObject json) {
                String msg = firstNonBlank(json.getStr("msg"), json.getStr("message"));
                if (StrUtil.isNotBlank(msg)) {
                    return "短信发送失败：" + trim(msg, 80);
                }
            }
        } catch (Exception ignored) {
        }
        return "短信发送失败，请稍后再试";
    }

    private String extractProviderCode(String body) {
        if (StrUtil.isBlank(body)) {
            return "";
        }
        try {
            Object parsed = JSONUtil.parse(body);
            if (parsed instanceof JSONObject json) {
                String msg = firstNonBlank(json.getStr("msg"), json.getStr("message"));
                String codeInMsg = firstSixDigitCode(msg);
                if (StrUtil.isNotBlank(codeInMsg)) {
                    return codeInMsg;
                }
            }
        } catch (Exception ignored) {
        }
        return firstSixDigitCode(body);
    }

    private String firstSixDigitCode(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)\\d{6}(?!\\d)")
                .matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : StrUtil.blankToDefault(second, "");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "unknown";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String trim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String sanitizeProviderBody(String text, int maxLength) {
        String trimmed = trim(text, maxLength);
        return trimmed.replaceAll("(?<!\\d)\\d{4,8}(?!\\d)", "******");
    }
}
