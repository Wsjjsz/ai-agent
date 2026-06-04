package com.aiagent.tools;

import com.aiagent.config.UrlSafety;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    private static final int TIMEOUT_MS = 10000; // 10秒超时
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            String safeUrl = UrlSafety.requireSafeHttpUrl(url).toString();
            Document document = Jsoup.connect(safeUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(false)
                    .get();

            // 优先提取正文文本，而非完整 HTML
            // 移除 script、style 等无用标签
            document.select("script, style, nav, footer, header, aside, iframe, noscript").remove();

            // 获取正文内容
            String title = document.title();
            String bodyText = document.body().text();

            // 组合结果
            StringBuilder result = new StringBuilder();
            if (title != null && !title.trim().isEmpty()) {
                result.append("Title: ").append(title).append("\n\n");
            }
            result.append("Content:\n").append(bodyText);

            return result.toString();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
