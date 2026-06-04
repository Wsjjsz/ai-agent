package com.aiagent.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RichPDFGenerationToolTest {

    @Test
    void generateRichPDFCreatesPdfAndPreview() {
        RichPDFGenerationTool tool = new RichPDFGenerationTool();

        String result = tool.generateRichPDF(
                "rich_pdf_test.pdf",
                "黄金投资机会分析",
                "最新新闻、行情与风险提示",
                "- 黄金受宏观预期影响波动较大\n- 建议关注美元指数与实际利率",
                "[{\"title\":\"黄金投资热度升温\",\"snippet\":\"投资者关注避险资产。\",\"link\":\"https://example.com/news\",\"source\":\"示例新闻\"}]",
                "[{\"symbol\":\"GC.F\",\"price\":4556.01,\"change\":\"\",\"changePercent\":\"\",\"date\":\"2026-05-29\",\"time\":\"09:45:28\"}]",
                "- 市场价格波动风险\n- 不构成投资建议",
                "[{\"title\":\"示例新闻\",\"link\":\"https://example.com/news\"}]"
        );

        JSONObject json = JSONUtil.parseObj(result);

        assertTrue(json.getBool("success"));
        assertTrue(Files.exists(Path.of(json.getStr("pdfPath"))));
        assertTrue(Files.exists(Path.of(json.getStr("previewPath"))));
    }
}
