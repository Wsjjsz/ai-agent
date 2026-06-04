package com.aiagent.controller;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.aiagent.config.UrlSafety;
import com.aiagent.service.HotNewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hotnews")
@Slf4j
public class HotNewsController {

    private final HotNewsService hotNewsService;

    public HotNewsController(HotNewsService hotNewsService) {
        this.hotNewsService = hotNewsService;
    }

    /**
     * 获取今日热点新闻列表
     */
    @GetMapping("/list")
    public List<Map<String, String>> list() {
        return hotNewsService.fetchHotNews();
    }

    /**
     * 图片代理（解决跨域问题）
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        try {
            String safeUrl = UrlSafety.requireSafeHttpUrl(url).toString();
            HttpResponse response = HttpUtil.createGet(safeUrl)
                    .timeout(5000)
                    .execute();
            String contentType = normalizeImageContentType(response.header(HttpHeaders.CONTENT_TYPE));
            if (contentType == null) {
                return ResponseEntity.badRequest().build();
            }
            byte[] bytes = response.bodyBytes();
            if (bytes.length > 2 * 1024 * 1024) {
                return ResponseEntity.status(413).build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(bytes);
        } catch (Exception e) {
            log.warn("图片代理失败: {}", url, e);
            return ResponseEntity.notFound().build();
        }
    }

    private String normalizeImageContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String lower = contentType.toLowerCase();
        if (lower.contains("image/png")) return "image/png";
        if (lower.contains("image/jpeg") || lower.contains("image/jpg")) return "image/jpeg";
        if (lower.contains("image/gif")) return "image/gif";
        if (lower.contains("image/webp")) return "image/webp";
        return null;
    }
}
