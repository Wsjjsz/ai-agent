package com.aiagent.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportArtifactToolTest {

    @Test
    void generateReportArtifactsCreatesDownloadableFiles() throws Exception {
        ReportArtifactTool tool = new ReportArtifactTool();
        Path chartPath = Files.createTempFile("report-chart", ".svg");
        Files.writeString(chartPath, """
                <svg xmlns="http://www.w3.org/2000/svg" width="640" height="320" viewBox="0 0 640 320">
                  <rect width="640" height="320" fill="#f8fafc"/>
                  <rect x="80" y="120" width="140" height="120" fill="#2563eb"/>
                  <rect x="260" y="80" width="140" height="160" fill="#16a34a"/>
                  <text x="80" y="64" font-size="28" fill="#172033">Chart</text>
                </svg>
                """, StandardCharsets.UTF_8);

        String result = tool.generateReportArtifacts(
                "artifact_report_test",
                "市场研究简报 ✅",
                "### 摘要标题\n✅ 市场情绪回暖，但仍需关注波动。",
                "[{\"title\":\"核心结论 ⚠️\",\"content\":\"### 估值判断\\n关注盈利质量和估值安全边际。\"}]",
                "{\"rows\":[[\"指标\",\"数值\"],[\"000001\",12.3],[\"000002\",\"8.9 🚀\"]]}",
                "[{\"title\":\"测试图表\",\"svgPath\":\"" + chartPath + "\",\"insight\":\"图表已经嵌入报告。\"}]",
                "[{\"title\":\"示例来源\",\"link\":\"https://example.com\"}]"
        );

        JSONObject json = JSONUtil.parseObj(result);

        assertTrue(json.getBool("success"));
        assertTrue(Files.exists(Path.of(json.getStr("markdownPath"))));
        assertTrue(Files.exists(Path.of(json.getStr("previewPath"))));
        assertTrue(Files.exists(Path.of(json.getStr("pdfPath"))));
        assertTrue(Files.exists(Path.of(json.getStr("docxPath"))));
        assertTrue(json.getJSONArray("files").size() == 4);
        assertTrue(!json.toString().contains("csvPath"));
        assertTrue(!json.toString().contains("xlsxPath"));
        assertTrue(!json.toString().contains("pptxPath"));

        String markdown = Files.readString(Path.of(json.getStr("markdownPath")), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("![测试图表](data:image/svg+xml;base64,"));
        assertTrue(markdown.contains("| 指标 | 数值 |"));
        assertTrue(!markdown.contains("文件："));

        try (ZipFile zipFile = new ZipFile(json.getStr("docxPath"))) {
            assertTrue(zipFile.getEntry("word/styles.xml") != null);
            assertTrue(zipFile.getEntry("word/media/image1.svg") != null);
            String documentXml = new String(zipFile.getInputStream(zipFile.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            String relsXml = new String(zipFile.getInputStream(zipFile.getEntry("word/_rels/document.xml.rels")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(!documentXml.contains("###"));
            assertTrue(documentXml.contains("Heading3"));
            assertTrue(documentXml.contains("摘要标题"));
            assertTrue(documentXml.contains("估值判断"));
            assertTrue(documentXml.contains("<w:tbl"));
            assertTrue(documentXml.contains("<w:drawing>"));
            assertTrue(relsXml.contains("relationships/image"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
