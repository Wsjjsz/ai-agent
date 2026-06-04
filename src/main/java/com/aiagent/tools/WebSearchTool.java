package com.aiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final int TIMEOUT_MS = 10000;
    private static final int DEFAULT_LIMIT = 5;

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        if (query == null || query.isBlank()) {
            return error("EMPTY_QUERY", "Search query cannot be empty.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return error("MISSING_API_KEY", "Search API key is not configured.");
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query.trim());
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            HttpResponse response = HttpUtil.createGet(SEARCH_API_URL)
                    .form(paramMap)
                    .timeout(TIMEOUT_MS)
                    .execute();
            String responseBody = response.body();
            if (response.getStatus() != 200) {
                return error("SEARCH_API_HTTP_" + response.getStatus(), extractErrorMessage(responseBody));
            }
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(responseBody);
            if (jsonObject.containsKey("error")) {
                return error("SEARCH_API_ERROR", jsonObject.getStr("error"));
            }
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "[]";
            }
            // 安全截取，防止越界
            int limit = Math.min(DEFAULT_LIMIT, organicResults.size());
            JSONArray results = JSONUtil.createArray();
            for (int i = 0; i < limit; i++) {
                JSONObject item = organicResults.getJSONObject(i);
                JSONObject normalized = JSONUtil.createObj();
                normalized.set("position", item.getInt("position", i + 1));
                normalized.set("title", item.getStr("title", ""));
                normalized.set("link", item.getStr("link", item.getStr("url", "")));
                normalized.set("snippet", item.getStr("snippet", item.getStr("description", "")));
                normalized.set("source", item.getStr("displayed_link", ""));
                normalized.set("provider", "searchapi-baidu");
                results.add(normalized);
            }
            return results.toString();
        } catch (Exception e) {
            return error("SEARCH_EXCEPTION", e.getMessage());
        }
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "Search failed." : message);
        error.set("provider", "searchapi-baidu");
        return error.toString();
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Search API request failed.";
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(body);
            String error = jsonObject.getStr("error");
            return error == null || error.isBlank() ? body : error;
        } catch (Exception ignored) {
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
    }
}
