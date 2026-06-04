package com.aiagent.auth;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class SmsCodeService {

    private static final String LOGIN_SCENE = "login";
    private static final String MAINLAND_CHINA_CODE = "+86";

    private final SmsLoginCodeRepository codeRepository;
    private final SmsCodeSender smsCodeSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.sms-code-secret:ai-agent-sms-local-secret-change-me}")
    private String smsCodeSecret;

    @Value("${app.auth.sms-code-store:db}")
    private String smsCodeStore;

    @Value("${app.auth.sms-ttl-seconds:300}")
    private long smsTtlSeconds;

    @Value("${app.auth.sms-resend-interval-seconds:60}")
    private long resendIntervalSeconds;

    @Value("${app.auth.sms-max-send-per-day:5}")
    private int maxSendPerDay;

    @Value("${app.auth.sms-max-send-per-ip-per-day:30}")
    private int maxSendPerIpPerDay;

    @Value("${app.auth.sms-max-send-per-device-per-day:10}")
    private int maxSendPerDevicePerDay;

    @Value("${app.auth.sms-max-attempts:5}")
    private int maxAttempts;

    public SmsCodeService(SmsLoginCodeRepository codeRepository, SmsCodeSender smsCodeSender,
                          ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.codeRepository = codeRepository;
        this.smsCodeSender = smsCodeSender;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void sendLoginCode(String rawPhone, String countryCode, String ipAddress, String deviceId) {
        String phone = normalizePhone(rawPhone, countryCode);
        if (trySendLoginCodeWithRedis(phone, ipAddress, deviceId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        codeRepository.findLatestCreateTime(phone, LOGIN_SCENE)
                .filter(latest -> latest.plusSeconds(resendIntervalSeconds).isAfter(now))
                .ifPresent(latest -> {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
                });

        int todayCount = codeRepository.countCreatedSince(phone, LOGIN_SCENE, now.minusHours(24));
        if (todayCount >= maxSendPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "今日验证码次数已用完，请明天再试");
        }

        if (codeRepository.countByIpSince(ipAddress, now.minusHours(24)) >= maxSendPerIpPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求过于频繁，请稍后再试");
        }

        if (codeRepository.countByDeviceSince(deviceId, now.minusHours(24)) >= maxSendPerDevicePerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前设备验证码次数已用完，请稍后再试");
        }

        String code = generateCode();
        codeRepository.create(phone, hash(phone, code), LOGIN_SCENE, now.plusSeconds(smsTtlSeconds),
                normalizeClientValue(ipAddress, 64), normalizeClientValue(deviceId, 128));
        smsCodeSender.sendLoginCode(phone, code);
    }

    @Transactional
    public String verifyAndConsumeLoginCode(String rawPhone, String countryCode, String code) {
        String phone = normalizePhone(rawPhone, countryCode);
        if (code == null || !code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码必须是 6 位数字");
        }
        Optional<String> redisPhone = tryVerifyAndConsumeWithRedis(phone, code);
        if (redisPhone.isPresent()) {
            return redisPhone.get();
        }

        SmsLoginCodeRepository.SmsCodeRecord record = codeRepository
                .findLatestValidCode(phone, LOGIN_SCENE, LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取"));

        if (record.attemptCount() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码尝试次数过多，请重新获取");
        }

        codeRepository.increaseAttempt(record.id());
        if (!constantTimeEquals(record.codeHash(), hash(phone, code))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码不正确");
        }

        if (!codeRepository.consume(record.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已失效，请重新获取");
        }
        return phone;
    }

    private boolean trySendLoginCodeWithRedis(String phone, String ipAddress, String deviceId) {
        if (!useRedisSmsCodeStore()) {
            return false;
        }
        try {
            String resendKey = smsKey("resend", phone);
            Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                    resendKey,
                    "1",
                    Duration.ofSeconds(resendIntervalSeconds)
            );
            if (Boolean.FALSE.equals(accepted)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
            }

            if (incrementWithTtl(smsKey("count:phone", phone), Duration.ofHours(24)) > maxSendPerDay) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "今日验证码次数已用完，请明天再试");
            }
            String ip = normalizeClientValue(ipAddress, 64);
            if (ip != null && incrementWithTtl(smsKey("count:ip", DigestUtil.sha256Hex(ip)), Duration.ofHours(24)) > maxSendPerIpPerDay) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求过于频繁，请稍后再试");
            }
            String device = normalizeClientValue(deviceId, 128);
            if (device != null && incrementWithTtl(smsKey("count:device", DigestUtil.sha256Hex(device)), Duration.ofHours(24)) > maxSendPerDevicePerDay) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前设备验证码次数已用完，请稍后再试");
            }

            String code = generateCode();
            String codeKey = smsKey("code", phone);
            redisTemplate.opsForHash().putAll(codeKey, Map.of(
                    "hash", hash(phone, code),
                    "attempts", "0"
            ));
            redisTemplate.expire(codeKey, Duration.ofSeconds(smsTtlSeconds));
            smsCodeSender.sendLoginCode(phone, code);
            return true;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Optional<String> tryVerifyAndConsumeWithRedis(String phone, String code) {
        if (!useRedisSmsCodeStore()) {
            return Optional.empty();
        }
        try {
            String codeKey = smsKey("code", phone);
            Object expectedHash = redisTemplate.opsForHash().get(codeKey, "hash");
            if (expectedHash == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
            }
            Long attempts = redisTemplate.opsForHash().increment(codeKey, "attempts", 1);
            if (attempts != null && attempts > maxAttempts) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码尝试次数过多，请重新获取");
            }
            if (!constantTimeEquals(String.valueOf(expectedHash), hash(phone, code))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "验证码不正确");
            }
            redisTemplate.delete(codeKey);
            return Optional.of(phone);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean useRedisSmsCodeStore() {
        return "redis".equalsIgnoreCase(smsCodeStore) && redisTemplate != null;
    }

    private long incrementWithTtl(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    private String smsKey(String type, String value) {
        return "sms:v1:" + LOGIN_SCENE + ":" + type + ":" + value;
    }

    public String normalizePhone(String rawPhone, String countryCode) {
        String country = countryCode == null || countryCode.isBlank() ? MAINLAND_CHINA_CODE : countryCode.trim();
        if (!MAINLAND_CHINA_CODE.equals(country)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂仅支持中国大陆手机号");
        }

        String phone = rawPhone == null ? "" : rawPhone.trim().replaceAll("[\\s-]", "");
        if (phone.startsWith(MAINLAND_CHINA_CODE)) {
            phone = phone.substring(MAINLAND_CHINA_CODE.length());
        } else if (phone.startsWith("86") && phone.length() == 13) {
            phone = phone.substring(2);
        }

        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的中国大陆手机号");
        }
        return MAINLAND_CHINA_CODE + phone;
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String hash(String phone, String code) {
        return DigestUtil.sha256Hex(phone + ":" + code + ":" + smsCodeSecret);
    }

    private String normalizeClientValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
