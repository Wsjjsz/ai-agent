package com.aiagent.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HotNewsService {

    private static final String TONGHUASHUN_API = "https://news.10jqka.com.cn/tapp/news/push/stock/";
    private static final int PAGE_SIZE = 60;
    private static final int DISPLAY_COUNT = 18;

    private final ChatModel chatModel;

    // 缓存所有财经新闻，每次刷新从中随机取 DISPLAY_COUNT 条
    private volatile List<Map<String, String>> cachedNews = Collections.emptyList();
    private volatile long lastFetchTime = 0;
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟缓存

    public HotNewsService(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 获取今日热点财经新闻（每次返回不同的随机子集）
     */
    public List<Map<String, String>> fetchHotNews() {
        // 如果缓存过期或为空，重新拉取
        if (cachedNews.isEmpty() || System.currentTimeMillis() - lastFetchTime > CACHE_TTL) {
            List<Map<String, String>> allNews = fetchFromTonghuashun();
            if (!allNews.isEmpty()) {
                // 先缓存原始新闻，避免 AI 摘要失败或超时导致前端热点刷新不出来
                cachedNews = allNews;
                long fetchTime = System.currentTimeMillis();
                lastFetchTime = fetchTime;
                enrichWithAiSummaryAsync(allNews, fetchTime);
            }
        }

        if (cachedNews.isEmpty()) {
            return Collections.emptyList();
        }

        // 从缓存中随机选取 DISPLAY_COUNT 条
        return randomSelect(cachedNews, DISPLAY_COUNT);
    }

    /**
     * 调用同花顺财经新闻 API
     */
    private List<Map<String, String>> fetchFromTonghuashun() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            String response = HttpUtil.createGet(TONGHUASHUN_API + "?page=1&tag=&track=website&pagesize=" + PAGE_SIZE)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .execute()
                    .body();

            JSONObject json = JSONUtil.parseObj(response);
            JSONObject data = json.getJSONObject("data");
            if (data == null) return Collections.emptyList();

            JSONArray list = data.getJSONArray("list");
            if (list == null || list.isEmpty()) return Collections.emptyList();

            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                String title = item.getStr("title", "");
                String digest = item.getStr("digest", "");
                String url = item.getStr("url", "");
                String picUrl = item.getStr("picUrl", "");
                Long ctime = item.getLong("ctime", 0L);

                if (title.isEmpty()) continue;

                Map<String, String> newsItem = new LinkedHashMap<>();
                newsItem.put("title", title);
                newsItem.put("summary", digest.length() > 80 ? digest.substring(0, 80) + "..." : digest);
                newsItem.put("sourceUrl", url);
                newsItem.put("imageUrl", picUrl);
                newsItem.put("pubTime", ctime > 0 ? String.valueOf(ctime * 1000) : "");
                result.add(newsItem);
            }
            return result;
        } catch (Exception e) {
            log.error("从同花顺获取财经新闻失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从列表中随机选取 count 条
     */
    private List<Map<String, String>> randomSelect(List<Map<String, String>> list, int count) {
        if (list.size() <= count) {
            return new ArrayList<>(list);
        }
        List<Map<String, String>> copy = new ArrayList<>(list);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return new ArrayList<>(copy.subList(0, count));
    }

    /**
     * AI 批量生成简洁摘要
     */
    private void enrichWithAiSummaryAsync(List<Map<String, String>> news, long fetchTime) {
        List<Map<String, String>> summaryTarget = news.stream()
                .map(LinkedHashMap::new)
                .map(map -> (Map<String, String>) map)
                .toList();
        CompletableFuture.supplyAsync(() -> enrichWithAiSummary(summaryTarget))
                .orTimeout(15, TimeUnit.SECONDS)
                .thenAccept(updated -> {
                    if (updated && lastFetchTime == fetchTime) {
                        cachedNews = summaryTarget;
                    }
                })
                .exceptionally(e -> {
                    log.warn("AI 热点摘要后台生成失败，继续使用原始新闻摘要: {}", e.getMessage());
                    return null;
                });
    }

    private boolean enrichWithAiSummary(List<Map<String, String>> news) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(news.size(), 30); // 最多处理30条，控制token
        for (int i = 0; i < limit; i++) {
            sb.append(i).append(". ").append(news.get(i).get("title")).append("\n");
        }

        String prompt = "你是金融新闻编辑。请根据以下财经新闻标题，为每条生成一个简洁的中文摘要（20-40字）。\n" +
                "要求：保留关键数据和信息，语言专业流畅，适合金融卡片展示。\n" +
                "严格按 JSON 数组格式返回，每个元素 {\"index\": 数字, \"summary\": \"摘要内容\"}。\n" +
                "不要输出其他内容。\n\n" + sb;

        try {
            String aiResponse = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            String jsonStr = extractJsonArray(aiResponse);
            if (jsonStr == null) return false;
            JSONArray arr = JSONUtil.parseArray(jsonStr);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int idx = obj.getInt("index", -1);
                String summary = obj.getStr("summary", "");
                if (idx >= 0 && idx < news.size() && !summary.isEmpty()) {
                    news.get(idx).put("summary", summary);
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("AI 摘要生成失败，使用原始 digest", e);
            return false;
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}
