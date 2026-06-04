package com.aiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchToolContractTest {

    @Test
    void webSearchRejectsEmptyQueryWithStructuredError() {
        WebSearchTool tool = new WebSearchTool("test-key");

        String result = tool.searchWeb("  ");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("EMPTY_QUERY"));
    }

    @Test
    void webSearchRejectsMissingApiKeyWithStructuredError() {
        WebSearchTool tool = new WebSearchTool("");

        String result = tool.searchWeb("黄金");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("MISSING_API_KEY"));
    }

    @Test
    void exaSearchRejectsEmptyQueryWithStructuredError() {
        ExaSearchTool tool = new ExaSearchTool("test-key");

        String result = tool.searchExa("  ");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("EMPTY_QUERY"));
    }

    @Test
    void exaSearchRejectsMissingApiKeyWithStructuredError() {
        ExaSearchTool tool = new ExaSearchTool("");

        String result = tool.searchExa("AI 投资趋势");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("MISSING_API_KEY"));
    }

    @Test
    void newsRssSearchRejectsEmptyQueryWithStructuredError() {
        NewsRssSearchTool tool = new NewsRssSearchTool();

        String result = tool.searchRecentNews("  ");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("EMPTY_QUERY"));
    }

    @Test
    void marketQuoteRejectsEmptySymbolsWithStructuredError() {
        MarketQuoteTool tool = new MarketQuoteTool();

        String result = tool.getMarketQuotes("  ");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("EMPTY_SYMBOLS"));
    }

    @Test
    void marketQuoteRejectsInvalidSymbolsWithStructuredError() {
        MarketQuoteTool tool = new MarketQuoteTool();

        String result = tool.getMarketQuotes("../secret");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("INVALID_SYMBOLS"));
    }
}
