package com.aiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 财经新闻搜索工具，用于给金融智能体补充近期市场信息。
 */
public class FinanceNewsSearchTool {

    private static final String TONGHUASHUN_API = "https://news.10jqka.com.cn/tapp/news/push/stock/";
    private static final int PAGE_SIZE = 60;
    private static final int RESULT_LIMIT = 8;
    private static final int TIMEOUT_MS = 10000;

    @Tool(description = "Search recent finance news and market hot topics")
    public String searchFinanceNews(
            @ToolParam(description = "Finance news keyword, for example: 黄金, A股, 美联储, 新能源") String query) {
        String keyword = query == null ? "" : query.trim();
        try {
            HttpResponse response = HttpUtil.createGet(TONGHUASHUN_API)
                    .form("page", "1")
                    .form("tag", "")
                    .form("track", "website")
                    .form("pagesize", String.valueOf(PAGE_SIZE))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .execute();

            if (response.getStatus() != 200) {
                return error("NEWS_HTTP_" + response.getStatus(), "Finance news request failed.");
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            JSONObject data = json.getJSONObject("data");
            JSONArray list = data == null ? null : data.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                return "[]";
            }

            JSONArray results = JSONUtil.createArray();
            for (int i = 0; i < list.size() && results.size() < RESULT_LIMIT; i++) {
                JSONObject item = list.getJSONObject(i);
                String title = item.getStr("title", "");
                String digest = item.getStr("digest", "");
                if (!matches(keyword, title, digest)) {
                    continue;
                }
                results.add(toResult(item, results.size() + 1));
            }

            if (results.isEmpty() && !keyword.isBlank()) {
                for (int i = 0; i < list.size() && results.size() < RESULT_LIMIT; i++) {
                    results.add(toResult(list.getJSONObject(i), results.size() + 1));
                }
            }

            return results.toString();
        } catch (Exception e) {
            return error("NEWS_EXCEPTION", e.getMessage());
        }
    }

    private boolean matches(String keyword, String title, String digest) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(title, keyword) || containsIgnoreCase(digest, keyword);
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private JSONObject toResult(JSONObject item, int position) {
        JSONObject result = JSONUtil.createObj();
        result.set("position", position);
        result.set("title", item.getStr("title", ""));
        result.set("snippet", item.getStr("digest", ""));
        result.set("link", item.getStr("url", ""));
        result.set("image", item.getStr("picUrl", ""));
        Long ctime = item.getLong("ctime", 0L);
        result.set("publishedAt", ctime > 0 ? ctime * 1000 : null);
        result.set("source", "同花顺财经");
        result.set("provider", "10jqka-finance-news");
        return result;
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "Finance news search failed." : message);
        error.set("provider", "10jqka-finance-news");
        return error.toString();
    }
}
