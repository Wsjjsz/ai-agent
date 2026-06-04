package com.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebScrapingToolTest {

    @Test
    void blocksUnsafeLocalUrlWithoutNetworkCall() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String url = "http://localhost:8123/api/health";
        String result = webScrapingTool.scrapeWebPage(url);
        Assertions.assertTrue(result.contains("Error scraping web page"));
    }
}
