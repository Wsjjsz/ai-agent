package com.aiagent.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartGenerationToolTest {

    @Test
    void generateChartCreatesSvgAndPreview() {
        ChartGenerationTool tool = new ChartGenerationTool();

        String result = tool.generateChart(
                "chart_test.svg",
                "行业涨跌幅",
                "bar",
                "[{\"label\":\"银行\",\"value\":1.2},{\"label\":\"AI\",\"value\":3.5}]",
                "AI 板块涨幅领先，银行表现稳健。"
        );

        JSONObject json = JSONUtil.parseObj(result);

        assertTrue(json.getBool("success"));
        assertTrue(Files.exists(Path.of(json.getStr("svgPath"))));
        assertTrue(Files.exists(Path.of(json.getStr("previewPath"))));
        assertTrue(json.getJSONArray("files").size() == 1);
        assertTrue("html".equals(json.getJSONArray("files").getJSONObject(0).getStr("type")));
    }
}
