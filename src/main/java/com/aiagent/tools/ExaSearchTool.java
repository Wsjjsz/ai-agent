package com.aiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Exa 网页搜索工具，适合获取高质量网页资料并进入报告生成链路。
 */
public class ExaSearchTool {

    private static final String EXA_SEARCH_URL = "https://api.exa.ai/search";
    private static final int TIMEOUT_MS = 15000;
    private static final int RESULT_LIMIT = 8;

    private final String apiKey;

    public ExaSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search high-quality web pages with Exa for research and report generation")
    public String searchExa(
            @ToolParam(description = "Search query, for example: 2026 AI investment trend, gold price outlook") String query) {
        if (query == null || query.isBlank()) {
            return error("EMPTY_QUERY", "Exa search query cannot be empty.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return error("MISSING_API_KEY", "Exa API key is not configured.");
        }

        try {
            JSONObject requestBody = JSONUtil.createObj();
            requestBody.set("query", query.trim());
            requestBody.set("numResults", RESULT_LIMIT);

            JSONObject contents = JSONUtil.createObj();
            contents.set("text", true);
            contents.set("summary", true);
            contents.set("highlights", true);
            requestBody.set("contents", contents);

            HttpResponse response = HttpUtil.createPost(EXA_SEARCH_URL)
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(TIMEOUT_MS)
                    .execute();

            String body = response.body();
            if (response.getStatus() != 200) {
                return error("EXA_HTTP_" + response.getStatus(), extractErrorMessage(body));
            }

            JSONObject json = JSONUtil.parseObj(body);
            JSONArray rawResults = json.getJSONArray("results");
            if (rawResults == null || rawResults.isEmpty()) {
                return "[]";
            }

            JSONArray results = JSONUtil.createArray();
            for (int i = 0; i < rawResults.size() && i < RESULT_LIMIT; i++) {
                JSONObject item = rawResults.getJSONObject(i);
                JSONObject result = JSONUtil.createObj();
                result.set("position", i + 1);
                result.set("title", item.getStr("title", ""));
                result.set("link", item.getStr("url", ""));
                result.set("snippet", firstNonBlank(
                        item.getStr("summary", ""),
                        trimText(item.getStr("text", ""), 500)
                ));
                result.set("publishedAt", item.getStr("publishedDate", ""));
                result.set("author", item.getStr("author", ""));
                result.set("image", item.getStr("image", ""));
                result.set("source", item.getStr("url", ""));
                result.set("provider", "exa");
                if (item.containsKey("highlights")) {
                    result.set("highlights", item.get("highlights"));
                }
                results.add(result);
            }
            return results.toString();
        } catch (Exception e) {
            return error("EXA_EXCEPTION", e.getMessage());
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String trimText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "Exa search failed." : message);
        error.set("provider", "exa");
        return error.toString();
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Exa request failed.";
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(body);
            String error = jsonObject.getStr("error");
            if (error != null && !error.isBlank()) {
                return error;
            }
            String message = jsonObject.getStr("message");
            return message == null || message.isBlank() ? body : message;
        } catch (Exception ignored) {
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
    }
}
