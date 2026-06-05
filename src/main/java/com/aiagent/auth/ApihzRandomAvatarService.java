package com.aiagent.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiagent.config.UrlSafety;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ApihzRandomAvatarService {

    private static final int MAX_AVATAR_OPTIONS = 12;

    private final String apiId;
    private final String apiKey;
    private final String endpoint;
    private final String imgType;
    private final int timeoutMs;
    private final Duration cacheTtl;
    private final List<AvatarOption> cachedOptions = new ArrayList<>();
    private Instant cacheExpiresAt = Instant.EPOCH;

    public ApihzRandomAvatarService(
            @Value("${apihz.id:}") String apiId,
            @Value("${apihz.key:}") String apiKey,
            @Value("${apihz.random-avatar.endpoint:https://cn.apihz.cn/api/img/apihzimgtx.php}") String endpoint,
            @Value("${apihz.random-avatar.img-type:0}") String imgType,
            @Value("${apihz.random-avatar.timeout-ms:8000}") int timeoutMs,
            @Value("${apihz.random-avatar.cache-ttl-seconds:1800}") long cacheTtlSeconds) {
        this.apiId = apiId;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.imgType = imgType;
        this.timeoutMs = Math.max(timeoutMs, 1000);
        this.cacheTtl = Duration.ofSeconds(Math.max(cacheTtlSeconds, 60));
    }

    public synchronized Optional<String> randomAvatarUrl() {
        if (Instant.now().isBefore(cacheExpiresAt) && !cachedOptions.isEmpty()) {
            AvatarOption option = cachedOptions.get(ThreadLocalRandom.current().nextInt(cachedOptions.size()));
            return Optional.of(option.url());
        }
        Optional<AvatarOption> avatar = fetchOneAvatar(1);
        avatar.ifPresent(option -> {
            cachedOptions.clear();
            cachedOptions.add(option);
            cacheExpiresAt = Instant.now().plus(cacheTtl);
        });
        return avatar.map(AvatarOption::url);
    }

    public synchronized List<AvatarOption> avatarOptions(int count) {
        return avatarOptions(count, false);
    }

    public synchronized List<AvatarOption> avatarOptions(int count, boolean refresh) {
        int safeCount = Math.max(1, Math.min(count, MAX_AVATAR_OPTIONS));
        if (!refresh && Instant.now().isBefore(cacheExpiresAt) && cachedOptions.size() >= safeCount) {
            return new ArrayList<>(cachedOptions.subList(0, safeCount));
        }
        List<AvatarOption> fresh = new ArrayList<>();
        for (int i = 0; i < safeCount; i++) {
            fetchOneAvatar(i + 1).ifPresent(fresh::add);
        }
        if (!fresh.isEmpty()) {
            cachedOptions.clear();
            cachedOptions.addAll(fresh);
            cacheExpiresAt = Instant.now().plus(cacheTtl);
            return fresh;
        }
        return new ArrayList<>(cachedOptions.subList(0, Math.min(cachedOptions.size(), safeCount)));
    }

    private Optional<AvatarOption> fetchOneAvatar(int position) {
        if (StrUtil.isBlank(apiId) || StrUtil.isBlank(apiKey) || StrUtil.isBlank(endpoint)) {
            return Optional.empty();
        }
        try {
            URI safeEndpoint = UrlSafety.requireSafeHttpUrl(endpoint);
            HttpResponse response = HttpUtil.createGet(safeEndpoint.toString())
                    .form("id", apiId)
                    .form("key", apiKey)
                    .form("imgtype", imgType)
                    .form("type", "1")
                    .timeout(timeoutMs)
                    .execute();
            if (!response.isOk()) {
                log.warn("APIHZ random avatar HTTP failure, status={}", response.getStatus());
                return Optional.empty();
            }
            String body = response.body();
            String imageUrl = "";
            try {
                JSONObject json = JSONUtil.parseObj(body);
                String code = json.getStr("code", "");
                if (StrUtil.isNotBlank(code) && !"200".equals(code) && !"1".equals(code) && !"0".equals(code)) {
                    log.warn("APIHZ random avatar provider failure: {}", trim(json.getStr("msg", ""), 120));
                    return Optional.empty();
                }
                imageUrl = firstNonBlank(
                        json.getStr("msg"),
                        firstNonBlank(json.getStr("url"),
                                firstNonBlank(json.getStr("image"), json.getStr("img")))
                );
            } catch (Exception ignored) {
                imageUrl = StrUtil.blankToDefault(body, "").trim();
            }
            if (!UrlSafety.isSafeHttpUrl(imageUrl)) {
                return Optional.empty();
            }
            return Optional.of(new AvatarOption(
                    "apihz-" + System.currentTimeMillis() + "-" + position,
                    "随机头像 " + position,
                    imageUrl,
                    "apihz-official-resource"
            ));
        } catch (Exception e) {
            log.warn("APIHZ random avatar fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first.trim() : StrUtil.blankToDefault(second, "");
    }

    private String trim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    public record AvatarOption(
            String id,
            String name,
            String url,
            String provider
    ) {
    }
}
