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

    @Test
    void generateChartRejectsAllZeroData() {
        ChartGenerationTool tool = new ChartGenerationTool();

        String result = tool.generateChart(
                "zero_chart.svg",
                "无有效数据图表",
                "bar",
                "[{\"label\":\"A\",\"value\":0},{\"label\":\"B\",\"value\":0}]",
                "无真实数值。"
        );

        JSONObject json = JSONUtil.parseObj(result);

        assertTrue(!json.getBool("success"));
        assertTrue("INSUFFICIENT_CHART_DATA".equals(json.getStr("code")));
    }

    @Test
    void generateChartParsesChineseRevenueStrings() {
        ChartGenerationTool tool = new ChartGenerationTool();

        String result = tool.generateChart(
                "revenue_chart.svg",
                "企业营收对比",
                "bar",
                "[{\"名称\":\"甲公司\",\"营收\":\"12.5亿元\"},{\"名称\":\"乙公司\",\"营收\":\"8.2亿元\"}]",
                "甲公司营收更高。"
        );

        JSONObject json = JSONUtil.parseObj(result);

        assertTrue(json.getBool("success"));
        assertTrue(Files.exists(Path.of(json.getStr("svgPath"))));
    }
}
