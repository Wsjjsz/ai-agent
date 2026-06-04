package com.aiagent.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 通用新闻 RSS 搜索工具，用于获取近期公开新闻。
 */
public class NewsRssSearchTool {

    private static final String BING_NEWS_RSS_URL = "https://www.bing.com/news/search?q=%s&format=rss";
    private static final int RESULT_LIMIT = 8;
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    @Tool(description = "Search recent news from public RSS sources")
    public String searchRecentNews(
            @ToolParam(description = "News keyword, for example: 美联储 降息, 英伟达 财报, 黄金价格") String query) {
        if (query == null || query.isBlank()) {
            return error("EMPTY_QUERY", "News query cannot be empty.");
        }
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = BING_NEWS_RSS_URL.formatted(encodedQuery);
            Document document = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            JSONArray results = JSONUtil.createArray();
            for (Element item : document.select("item")) {
                if (results.size() >= RESULT_LIMIT) {
                    break;
                }
                JSONObject result = JSONUtil.createObj();
                result.set("position", results.size() + 1);
                result.set("title", item.selectFirst("title") == null ? "" : item.selectFirst("title").text());
                result.set("link", item.selectFirst("link") == null ? "" : item.selectFirst("link").text());
                result.set("snippet", extractPlainText(item, "description"));
                result.set("source", extractPlainText(item, "source"));
                result.set("publishedAt", item.selectFirst("pubDate") == null ? "" : item.selectFirst("pubDate").text());
                result.set("provider", "bing-news-rss");
                results.add(result);
            }
            return results.toString();
        } catch (Exception e) {
            return error("NEWS_RSS_EXCEPTION", e.getMessage());
        }
    }

    private String extractPlainText(Element item, String selector) {
        Element element = item.selectFirst(selector);
        if (element == null) {
            return "";
        }
        return Jsoup.parse(element.text()).text();
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "News search failed." : message);
        error.set("provider", "bing-news-rss");
        return error.toString();
    }
}
