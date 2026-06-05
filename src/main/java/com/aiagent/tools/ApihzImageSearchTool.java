package com.aiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiagent.config.UrlSafety;
import com.aiagent.files.GeneratedFileContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * APIHZ image search tool. It is useful when the agent needs real images for
 * answers, previews, or report materials.
 */
public class ApihzImageSearchTool {

    private static final int TIMEOUT_MS = 12000;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final HttpClient IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String apiId;
    private final String apiKey;
    private final String endpoint;

    public ApihzImageSearchTool(String apiId, String apiKey, String endpoint) {
        this.apiId = apiId;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
    }

    @Tool(description = "API盒子通用图片搜索-百度源。按关键词搜索真实图片素材。生成图文报告、需要报告配图、产品/地点/人物/行业实图、金融主题视觉素材时优先使用。返回 JSON 图片条目，包含远程图片 URL 和尽量下载后的 localPath，可传给 generateReportArtifacts 的 imagesJson。")
    public String searchImages(
            @ToolParam(description = "Image search keyword. Use concrete Chinese keywords tied to the report topic, for example: 黄金 金条 市场, 股票交易 大盘, 基金 投资组合, 家庭保险 保障, 宏观经济 数据") String query,
            @ToolParam(description = "Result page number, starting from 1. Use 1 by default.") Integer page,
            @ToolParam(description = "Number of images to return, 1-20. Use 6 by default; for formal reports prefer 3-6 relevant images.") Integer limit,
            @ToolParam(description = "Image type: 1 for thumbnail/preview image, 2 for original/source image. Use 1 for chat display, 2 only when high-resolution source images are needed.") Integer type) {
        if (query == null || query.isBlank()) {
            return error("EMPTY_QUERY", "Image search query cannot be empty.");
        }
        if (apiId == null || apiId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return error("MISSING_APIHZ_CREDENTIALS", "APIHZ id/key is not configured.");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 6 : Math.min(limit, MAX_LIMIT);
        int safeType = type != null && type == 2 ? 2 : 1;

        try {
            cn.hutool.http.HttpResponse response = HttpUtil.createGet(endpoint)
                    .form("id", apiId)
                    .form("key", apiKey)
                    .form("words", query.trim())
                    .form("page", String.valueOf(safePage))
                    .form("limit", String.valueOf(safeLimit))
                    .form("type", String.valueOf(safeType))
                    .timeout(TIMEOUT_MS)
                    .execute();
            String body = response.body();
            if (response.getStatus() != 200) {
                return error("APIHZ_HTTP_" + response.getStatus(), trim(body, 500));
            }
            JSONObject json = JSONUtil.parseObj(body);
            if (isErrorResponse(json)) {
                return error("APIHZ_ERROR", firstNonBlank(json.getStr("msg"), json.getStr("message")));
            }

            JSONArray rawImages = extractImageArray(json);
            if (rawImages == null || rawImages.isEmpty()) {
                return "[]";
            }

            JSONArray results = JSONUtil.createArray();
            for (int i = 0; i < rawImages.size() && results.size() < safeLimit; i++) {
                Object raw = rawImages.get(i);
                JSONObject item = normalizeImageItem(raw, results.size() + 1, query.trim(), safeType);
                if (item != null) {
                    results.add(item);
                }
            }
            return results.toString();
        } catch (Exception e) {
            return error("APIHZ_EXCEPTION", e.getMessage());
        }
    }

    private boolean isErrorResponse(JSONObject json) {
        Integer code = json.getInt("code");
        if (code == null) {
            code = json.getInt("status");
        }
        return code != null && code != 200 && code != 1 && code != 0;
    }

    private JSONArray extractImageArray(JSONObject json) {
        Object res = firstPresent(json, "res", "data", "result", "images", "list");
        if (res instanceof JSONArray array) {
            return array;
        }
        if (res instanceof JSONObject object) {
            Object nested = firstPresent(object, "res", "data", "result", "images", "list");
            if (nested instanceof JSONArray array) {
                return array;
            }
        }
        return null;
    }

    private Object firstPresent(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return json.get(key);
            }
        }
        return null;
    }

    private JSONObject normalizeImageItem(Object raw, int position, String query, int type) {
        String imageUrl;
        String thumbnailUrl = "";
        String title = query;
        String source = "";
        if (raw instanceof JSONObject object) {
            imageUrl = firstNonBlank(
                    object.getStr("image"),
                    firstNonBlank(object.getStr("url"),
                            firstNonBlank(object.getStr("img"),
                                    firstNonBlank(object.getStr("pic"),
                                            firstNonBlank(object.getStr("objURL"),
                                                    firstNonBlank(object.getStr("objurl"),
                                                            firstNonBlank(object.getStr("middleURL"), object.getStr("middleurl")))))))
            );
            thumbnailUrl = firstNonBlank(
                    object.getStr("thumbnail"),
                    firstNonBlank(object.getStr("thumb"),
                            firstNonBlank(object.getStr("thumbURL"),
                                    firstNonBlank(object.getStr("thumburl"),
                                            firstNonBlank(object.getStr("preview"),
                                                    firstNonBlank(object.getStr("hoverURL"),
                                                            firstNonBlank(object.getStr("hoverurl"), imageUrl))))))
            );
            title = firstNonBlank(object.getStr("title"), firstNonBlank(object.getStr("name"), query));
            source = firstNonBlank(object.getStr("source"),
                    firstNonBlank(object.getStr("from"), firstNonBlank(object.getStr("fromURLHost"), object.getStr("fromurlhost"))));
        } else {
            imageUrl = raw == null ? "" : raw.toString();
            thumbnailUrl = imageUrl;
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        JSONObject result = JSONUtil.createObj();
        result.set("position", position);
        result.set("title", title);
        result.set("image", imageUrl);
        result.set("thumbnail", thumbnailUrl);
        result.set("link", imageUrl);
        String localPath = downloadImageToLocal(firstNonBlank(thumbnailUrl, imageUrl), position, query);
        if (localPath.isBlank() && !thumbnailUrl.equals(imageUrl)) {
            localPath = downloadImageToLocal(imageUrl, position, query);
        }
        if (!localPath.isBlank()) {
            result.set("localPath", localPath);
        }
        result.set("source", source);
        result.set("query", query);
        result.set("type", type == 2 ? "source" : "preview");
        result.set("provider", "apihz-image-search");
        return result;
    }

    private String downloadImageToLocal(String imageUrl, int position, String query) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "";
        }
        try {
            URI safeUri = UrlSafety.requireSafeHttpUrl(imageUrl);
            java.net.http.HttpResponse<byte[]> response = sendImageRequestFollowingSafeRedirects(safeUri, 3);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return "";
            }
            String extension = imageExtensionFromContentType(response.headers().firstValue("content-type").orElse(""));
            if (extension.isBlank()) {
                extension = imageExtensionFromBytes(bytes);
            }
            if (extension.isBlank() || "webp".equals(extension)) {
                return "";
            }
            String safeName = normalizeFileName(query) + "_" + position + "_" + System.currentTimeMillis() + "." + extension;
            Path imagePath = GeneratedFileContext.resolve("image", safeName);
            Files.createDirectories(imagePath.getParent());
            Files.write(imagePath, bytes);
            return imagePath.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private java.net.http.HttpResponse<byte[]> sendImageRequestFollowingSafeRedirects(URI startUri, int maxRedirects) throws Exception {
        URI current = startUri;
        for (int i = 0; i <= maxRedirects; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AI-Agent-Image/1.0)")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .GET()
                    .build();
            java.net.http.HttpResponse<byte[]> response = IMAGE_HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (!isRedirectStatus(response.statusCode())) {
                return response;
            }
            String location = response.headers().firstValue("location").orElse("");
            if (location.isBlank() || i == maxRedirects) {
                return response;
            }
            current = UrlSafety.requireSafeHttpUrl(current.resolve(location).normalize().toString());
        }
        throw new IllegalStateException("Image redirect limit exceeded");
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String imageExtensionFromContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("image/png")) return "png";
        if (normalized.contains("image/jpeg") || normalized.contains("image/jpg")) return "jpg";
        if (normalized.contains("image/gif")) return "gif";
        if (normalized.contains("image/svg")) return "svg";
        if (normalized.contains("image/webp")) return "webp";
        return "";
    }

    private String imageExtensionFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return "";
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return "png";
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return "jpg";
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) return "gif";
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) return "webp";
        String prefix = new String(bytes, 0, Math.min(bytes.length, 200)).trim().toLowerCase(Locale.ROOT);
        if (prefix.startsWith("<svg") || prefix.contains("<svg")) return "svg";
        return "";
    }

    private String normalizeFileName(String value) {
        String normalized = value == null ? "image" : value.trim();
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_-]+", "_");
        if (normalized.isBlank()) {
            normalized = "image";
        }
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "APIHZ image search failed." : message);
        error.set("provider", "apihz-image-search");
        return error.toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String trim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
