package com.aiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行情报价工具，用于获取股票、ETF、商品期货等公开报价。
 */
public class MarketQuoteTool {

    private static final String STOOQ_QUOTE_URL = "https://stooq.com/q/l/";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_SYMBOLS = 10;

    @Tool(description = "Get recent market quotes for US stocks, ETFs, and commodity futures")
    public String getMarketQuotes(
            @ToolParam(description = "Comma separated symbols, for example: AAPL,MSFT,GC=F,CL=F or stooq symbols like aapl.us,gc.f") String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return error("EMPTY_SYMBOLS", "Symbols cannot be empty.");
        }
        List<String> normalizedSymbols = normalizeSymbols(symbols);
        if (normalizedSymbols.isEmpty()) {
            return error("INVALID_SYMBOLS", "No valid symbols found.");
        }
        try {
            JSONArray results = JSONUtil.createArray();
            for (String symbol : normalizedSymbols) {
                JSONObject quote = fetchStooqQuote(symbol, results.size() + 1);
                if (quote != null) {
                    results.add(quote);
                }
            }
            return results.toString();
        } catch (Exception e) {
            return error("QUOTE_EXCEPTION", e.getMessage());
        }
    }

    private List<String> normalizeSymbols(String symbols) {
        return Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(this::toStooqSymbol)
                .filter(symbol -> symbol.matches("[A-Za-z0-9.\\-]+"))
                .limit(MAX_SYMBOLS)
                .collect(Collectors.toList());
    }

    private String toStooqSymbol(String symbol) {
        String normalized = symbol.trim().toLowerCase();
        if (normalized.endsWith("=f")) {
            return normalized.substring(0, normalized.length() - 2) + ".f";
        }
        if (!normalized.contains(".") && normalized.matches("[a-z]{1,5}")) {
            return normalized + ".us";
        }
        return normalized;
    }

    private JSONObject fetchStooqQuote(String symbol, int position) {
        HttpResponse response = HttpUtil.createGet(STOOQ_QUOTE_URL)
                .form("s", symbol)
                .form("f", "sd2t2ohlcv")
                .form("e", "csv")
                .timeout(TIMEOUT_MS)
                .execute();
        if (response.getStatus() != 200) {
            return null;
        }
        String[] values = response.body().trim().split(",", -1);
        if (values.length < 8 || values[1].equalsIgnoreCase("N/D")) {
            return null;
        }
        JSONObject result = JSONUtil.createObj();
        result.set("position", position);
        result.set("symbol", values[0]);
        result.set("date", values[1]);
        result.set("time", values[2]);
        result.set("open", toNumber(values[3]));
        result.set("high", toNumber(values[4]));
        result.set("low", toNumber(values[5]));
        result.set("price", toNumber(values[6]));
        result.set("volume", toNumber(values[7]));
        result.set("provider", "stooq-quote");
        return result;
    }

    private Object toNumber(String value) {
        if (StrUtil.isBlank(value) || "N/D".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String error(String code, String message) {
        JSONObject error = JSONUtil.createObj();
        error.set("success", false);
        error.set("code", code);
        error.set("message", message == null || message.isBlank() ? "Quote request failed." : message);
        error.set("provider", "stooq-quote");
        return error.toString();
    }
}
