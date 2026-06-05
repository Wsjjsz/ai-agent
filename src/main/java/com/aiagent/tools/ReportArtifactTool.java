package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiagent.config.UrlSafety;
import com.aiagent.files.GeneratedFileContext;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.svg.converter.SvgConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates multi-format report artifacts for preview and download.
 */
@Slf4j
public class ReportArtifactTool {

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_REPORT_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final HttpClient IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Tool(description = "Generate downloadable final-answer report artifacts from a final summary, expanded analysis sections, tables, charts, relevant images, and references. The summary must be the user's final answer/core conclusion, not reasoning steps, tool logs, file paths, or generation notices. sectionsJson must expand that final answer with richer analysis. If searchImages was called earlier and returned images, pass 2-4 directly relevant items in imagesJson, preferably using localPath, so Markdown, HTML, PDF, and Word render actual images instead of plain URLs. If imagesJson is empty, this tool will try to reuse a small number of images downloaded in the current session. Outputs only Markdown, PDF, HTML preview, and Word DOCX.", returnDirect = false)
    public String generateReportArtifacts(
            @ToolParam(description = "Base file name without path, for example: ai-market-report") String fileName,
            @ToolParam(description = "Report title") String title,
            @ToolParam(description = "Final answer/core conclusion in Markdown or plain text. For formal reports, write 200-400 Chinese characters with conclusion, opportunity, suitable users, and key risks. Do not include reasoning process, tool call logs, file generation notices, or download paths.") String summary,
            @ToolParam(description = "JSON array of expanded report sections: [{\"title\":\"...\",\"content\":\"...\"}]. For formal reports, include at least 6 sections and make each section substantive, usually 300-600 Chinese characters. These sections must expand the final answer, not describe execution steps or tool usage.") String sectionsJson,
            @ToolParam(description = "JSON array of table rows, or object with rows field. Use this for real comparisons, allocation plans, risk matrices, timelines, or product/asset comparisons.") String tablesJson,
            @ToolParam(description = "JSON array of chart artifacts: [{\"title\":\"...\",\"path\":\"...\",\"previewPath\":\"...\",\"insight\":\"...\"}]") String chartsJson,
            @ToolParam(description = "JSON array of report images from searchImages: [{\"title\":\"...\",\"localPath\":\"/tmp/...jpg\",\"image\":\"https://...\",\"thumbnail\":\"https://...\",\"description\":\"...\",\"source\":\"...\"}]. If searchImages returned localPath, include it. For visually meaningful reports, pass 2-4 highly relevant images that explain the topic. Use [] only when no relevant image is needed or image search failed.") String imagesJson,
            @ToolParam(description = "JSON array of references: [{\"title\":\"...\",\"link\":\"...\"}]") String referencesJson) {
        try {
            String baseName = normalizeBaseName(fileName);
            Path mdPath = GeneratedFileContext.resolve("file", baseName + ".md");
            Path htmlPath = GeneratedFileContext.resolve("preview", baseName + ".html");
            Path pdfPath = GeneratedFileContext.resolve("pdf", baseName + ".pdf");
            Path docxPath = GeneratedFileContext.resolve("file", baseName + ".docx");

            FileUtil.mkdir(mdPath.getParent().toString());
            FileUtil.mkdir(htmlPath.getParent().toString());
            FileUtil.mkdir(pdfPath.getParent().toString());
            FileUtil.mkdir(docxPath.getParent().toString());

            JSONArray sections = parseArray(sectionsJson);
            JSONArray rows = parseRows(tablesJson);
            JSONArray charts = parseArray(chartsJson);
            JSONArray images = parseArray(imagesJson);
            if (images.isEmpty()) {
                images = discoverSessionImages();
            }
            JSONArray references = parseArray(referencesJson);

            JSONObject payload = JSONUtil.createObj();
            payload.set("title", defaultIfBlank(title, "智能体研究报告"));
            payload.set("summary", defaultIfBlank(summary, ""));
            payload.set("sections", sections);
            payload.set("tables", rows);
            payload.set("charts", charts);
            payload.set("images", images);
            payload.set("references", references);
            payload.set("generatedAt", LocalDateTime.now(REPORT_ZONE).format(REPORT_TIME_FORMATTER));

            FileUtil.writeString(buildMarkdown(payload), mdPath.toFile(), StandardCharsets.UTF_8);
            FileUtil.writeString(buildHtml(payload), htmlPath.toFile(), StandardCharsets.UTF_8);
            writePdf(payload, pdfPath);
            writeDocx(payload, docxPath);

            JSONObject result = JSONUtil.createObj();
            result.set("success", true);
            result.set("type", "artifact_bundle");
            result.set("title", payload.getStr("title"));
            result.set("markdownPath", mdPath.toString());
            result.set("previewPath", htmlPath.toString());
            result.set("pdfPath", pdfPath.toString());
            result.set("docxPath", docxPath.toString());
            result.set("files", files(
                    file("Markdown 报告", "md", mdPath.toString(), true),
                    file("PDF 正式报告", "pdf", pdfPath.toString(), true),
                    file("HTML 预览", "html", htmlPath.toString(), true),
                    file("Word 文档", "docx", docxPath.toString(), false)
            ));
            result.set("generatedAt", payload.getStr("generatedAt"));
            return result.toString();
        } catch (Exception e) {
            log.warn("Report artifact generation failed", e);
            JSONObject error = JSONUtil.createObj();
            error.set("success", false);
            error.set("code", "REPORT_ARTIFACT_GENERATION_FAILED");
            error.set("message", e.getMessage());
            return error.toString();
        }
    }

    private String buildMarkdown(JSONObject payload) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(payload.getStr("title", "智能体研究报告")).append("\n\n");
        md.append("> 生成时间：").append(payload.getStr("generatedAt", "")).append("\n\n");
        md.append("## 最终总结\n\n").append(defaultIfBlank(payload.getStr("summary", ""), "暂无最终总结。")).append("\n\n");
        appendMarkdownImages(md, payload.getJSONArray("images"));

        JSONArray sections = payload.getJSONArray("sections");
        if (sections != null && !sections.isEmpty()) {
            md.append("## 扩展分析\n\n");
        }
        for (int i = 0; sections != null && i < sections.size(); i++) {
            JSONObject section = toObject(sections.get(i), "章节 " + (i + 1));
            md.append("### ").append(section.getStr("title", "章节 " + (i + 1))).append("\n\n")
                    .append(section.getStr("content", "")).append("\n\n");
        }

        JSONArray charts = payload.getJSONArray("charts");
        if (charts != null && !charts.isEmpty()) {
            md.append("## 可视化分析\n\n");
            for (int i = 0; i < charts.size(); i++) {
                JSONObject chart = toObject(charts.get(i), "可视化 " + (i + 1));
                String chartTitle = chart.getStr("title", "可视化 " + (i + 1));
                String path = chartImagePath(chart);
                md.append("### ").append(chartTitle).append("\n\n");
                String imageUri = markdownImageUri(path);
                if (StrUtil.isNotBlank(imageUri)) {
                    md.append("![").append(escapeMarkdownAlt(chartTitle)).append("](").append(imageUri).append(")\n\n");
                }
                md.append(defaultIfBlank(chart.getStr("insight", ""), "暂无图表解读。")).append("\n\n");
            }
        }

        JSONArray rows = payload.getJSONArray("tables");
        if (rows != null && !rows.isEmpty()) {
            md.append("## 数据表格\n\n");
            md.append(buildMarkdownTable(rows)).append("\n\n");
        }

        JSONArray references = payload.getJSONArray("references");
        if (references != null && !references.isEmpty()) {
            md.append("## 来源引用\n\n");
            for (int i = 0; i < references.size(); i++) {
                JSONObject ref = toObject(references.get(i), "来源 " + (i + 1));
                md.append(i + 1).append(". ").append(firstNonBlank(ref.getStr("title", ""), ref.getStr("link", "")));
                if (StrUtil.isNotBlank(ref.getStr("link", ""))) {
                    md.append(" - ").append(ref.getStr("link", ""));
                }
                md.append("\n");
            }
        }
        return md.toString();
    }

    private String buildHtml(JSONObject payload) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>报告预览</title>
                  <style>
                    body{margin:0;background:#eef2f7;color:#172033;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}
                    main{max-width:980px;margin:24px auto;background:#fff;border:1px solid #e2e8f0;}
                    header{padding:46px 52px;background:#172033;color:#fff;}
                    h1{margin:0;font-size:32px;line-height:1.2} h2{font-size:20px;color:#1e3a8a;margin:0 0 16px}
                    section{padding:30px 52px;border-bottom:1px solid #e5e7eb;}
                    .summary{background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:16px;line-height:1.75;white-space:pre-wrap}
                    .section-text{line-height:1.8;white-space:pre-wrap}.chart{margin:18px 0}.chart img{width:100%;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc}
                    .image-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px}.report-image{margin:0}.report-image img{width:100%;max-height:260px;object-fit:cover;border:1px solid #e2e8f0;background:#f8fafc}.report-image figcaption{font-size:13px;color:#475569;margin-top:8px;line-height:1.5}
                    table{width:100%;border-collapse:collapse}th,td{border:1px solid #e2e8f0;padding:10px;text-align:left;font-size:13px}th{background:#eef2ff;color:#3730a3}
                    a{color:#2563eb}.muted{color:#64748b;font-size:12px}
                  </style>
                </head><body><main>
                """);
        html.append("<header><h1>").append(escape(payload.getStr("title", "智能体研究报告"))).append("</h1><p>")
                .append(escape(payload.getStr("generatedAt", ""))).append("</p></header>");
        html.append("<section><h2>最终总结</h2><div class=\"summary\">")
                .append(markdownToHtml(defaultIfBlank(payload.getStr("summary", ""), "暂无最终总结。"))).append("</div></section>");
        appendHtmlImages(html, payload.getJSONArray("images"));

        JSONArray sections = payload.getJSONArray("sections");
        if (sections != null && !sections.isEmpty()) {
            html.append("<section><h2>扩展分析</h2><p class=\"muted\">以下内容基于最终总结展开，补充背景、策略、风险和执行建议。</p></section>");
        }
        for (int i = 0; sections != null && i < sections.size(); i++) {
            JSONObject section = toObject(sections.get(i), "章节 " + (i + 1));
            html.append("<section><h2>").append(escape(section.getStr("title", "章节 " + (i + 1))))
                    .append("</h2><div class=\"section-text\">").append(markdownToHtml(section.getStr("content", "")))
                    .append("</div></section>");
        }

        JSONArray charts = payload.getJSONArray("charts");
        if (charts != null && !charts.isEmpty()) {
            html.append("<section><h2>可视化分析</h2>");
            for (int i = 0; i < charts.size(); i++) {
                JSONObject chart = toObject(charts.get(i), "可视化 " + (i + 1));
                String path = chartImagePath(chart);
                html.append("<div class=\"chart\"><h3>").append(escape(chart.getStr("title", "可视化 " + (i + 1)))).append("</h3>");
                String imageSrc = htmlImageSrc(path);
                if (StrUtil.isNotBlank(imageSrc)) {
                    html.append("<img src=\"").append(escapeAttr(imageSrc)).append("\" alt=\"chart\">");
                }
                html.append("<p>").append(escape(chart.getStr("insight", ""))).append("</p></div>");
            }
            html.append("</section>");
        }

        JSONArray rows = payload.getJSONArray("tables");
        if (rows != null && !rows.isEmpty()) {
            html.append("<section><h2>数据表格</h2>").append(buildHtmlTable(rows)).append("</section>");
        }

        JSONArray references = payload.getJSONArray("references");
        if (references != null && !references.isEmpty()) {
            html.append("<section><h2>来源引用</h2>");
            for (int i = 0; i < references.size(); i++) {
                JSONObject ref = toObject(references.get(i), "来源 " + (i + 1));
                String link = ref.getStr("link", "");
                html.append("<p>").append(i + 1).append(". ").append(escape(firstNonBlank(ref.getStr("title", ""), link)));
                if (StrUtil.isNotBlank(link) && UrlSafety.isSafeHttpUrl(link)) {
                    html.append("<br><a href=\"").append(escapeAttr(link)).append("\" target=\"_blank\" rel=\"noopener\">")
                            .append(escape(link)).append("</a>");
                }
                html.append("</p>");
            }
            html.append("</section>");
        }
        html.append("</main></body></html>");
        return html.toString();
    }

    private String buildHtmlTable(JSONArray rows) {
        JSONArray normalizedRows = normalizeRows(rows);
        Set<String> keys = collectKeys(normalizedRows);
        StringBuilder html = new StringBuilder("<table><thead><tr>");
        keys.forEach(key -> html.append("<th>").append(escape(key)).append("</th>"));
        html.append("</tr></thead><tbody>");
        for (int i = 0; i < normalizedRows.size(); i++) {
            JSONObject row = toObject(normalizedRows.get(i), "Row " + (i + 1));
            html.append("<tr>");
            keys.forEach(key -> html.append("<td>").append(escape(String.valueOf(defaultIfNull(row.get(key), "")))).append("</td>"));
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private void writePdf(JSONObject payload, Path pdfPath) throws IOException {
        try (PdfWriter writer = new PdfWriter(pdfPath.toFile());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            document.setFont(font);

            document.add(new Paragraph(pdfSafeText(payload.getStr("title", "智能体研究报告")))
                    .setFontSize(24)
                    .setFontColor(new DeviceRgb(30, 58, 138))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(40));
            document.add(new Paragraph(pdfSafeText(payload.getStr("generatedAt", "")))
                    .setFontSize(10)
                    .setFontColor(new DeviceRgb(100, 116, 139))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(28));

            addPdfHeading(document, "最终总结", 1);
            addMarkdownToPdf(document, defaultIfBlank(payload.getStr("summary", ""), "暂无最终总结。"));
            addPdfReportImages(document, pdf, payload.getJSONArray("images"));

            JSONArray sections = payload.getJSONArray("sections");
            if (sections != null && !sections.isEmpty()) {
                addPdfHeading(document, "扩展分析", 1);
            }
            for (int i = 0; sections != null && i < sections.size(); i++) {
                JSONObject section = toObject(sections.get(i), "章节 " + (i + 1));
                addPdfHeading(document, section.getStr("title", "章节 " + (i + 1)), 2);
                addMarkdownToPdf(document, section.getStr("content", ""));
            }

            JSONArray charts = payload.getJSONArray("charts");
            if (charts != null && !charts.isEmpty()) {
                addPdfHeading(document, "可视化分析", 1);
                for (int i = 0; i < charts.size(); i++) {
                    JSONObject chart = toObject(charts.get(i), "可视化 " + (i + 1));
                    addPdfHeading(document, chart.getStr("title", "可视化 " + (i + 1)), 2);
                    addPdfChartImage(document, pdf, chartImagePath(chart));
                    addMarkdownToPdf(document, chart.getStr("insight", ""));
                }
            }

            JSONArray rows = payload.getJSONArray("tables");
            if (rows != null && !rows.isEmpty()) {
                addPdfHeading(document, "数据表格", 1);
                addPdfTable(document, rows);
            }

            JSONArray references = payload.getJSONArray("references");
            if (references != null && !references.isEmpty()) {
                addPdfHeading(document, "来源引用", 1);
                for (int i = 0; i < references.size(); i++) {
                    JSONObject ref = toObject(references.get(i), "来源 " + (i + 1));
                    document.add(new Paragraph(pdfSafeText((i + 1) + ". " + firstNonBlank(ref.getStr("title", ""), ref.getStr("link", "")) + " " + ref.getStr("link", "")))
                            .setFontSize(9)
                            .setFontColor(new DeviceRgb(71, 85, 105)));
                }
            }
        } catch (Exception e) {
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void writeDocx(JSONObject payload, Path docxPath) throws IOException {
        DocxContext docx = new DocxContext();
        StringBuilder body = docx.body;
        body.append(docxHeading(payload.getStr("title", "智能体研究报告"), 1));
        body.append(docxParagraph("生成时间：" + payload.getStr("generatedAt", "")));
        body.append(docxHeading("最终总结", 2));
        appendDocxMarkdown(body, defaultIfBlank(payload.getStr("summary", ""), "暂无最终总结。"));
        appendDocxReportImages(docx, payload.getJSONArray("images"));

        JSONArray sections = payload.getJSONArray("sections");
        if (sections != null && !sections.isEmpty()) {
            body.append(docxHeading("扩展分析", 2));
        }
        for (int i = 0; sections != null && i < sections.size(); i++) {
            JSONObject section = toObject(sections.get(i), "章节 " + (i + 1));
            body.append(docxHeading(section.getStr("title", "章节 " + (i + 1)), 3));
            appendDocxMarkdown(body, section.getStr("content", ""));
        }

        JSONArray charts = payload.getJSONArray("charts");
        if (charts != null && !charts.isEmpty()) {
            body.append(docxHeading("可视化分析", 2));
            for (int i = 0; i < charts.size(); i++) {
                JSONObject chart = toObject(charts.get(i), "可视化 " + (i + 1));
                body.append(docxHeading(chart.getStr("title", "可视化 " + (i + 1)), 3));
                appendDocxImage(docx, chartImagePath(chart), chart.getStr("title", "可视化 " + (i + 1)));
                appendDocxMarkdown(body, chart.getStr("insight", ""));
            }
        }

        JSONArray rows = payload.getJSONArray("tables");
        if (rows != null && !rows.isEmpty()) {
            body.append(docxHeading("数据表格", 2));
            body.append(docxTable(rows));
        }

        JSONArray references = payload.getJSONArray("references");
        if (references != null && !references.isEmpty()) {
            body.append(docxHeading("来源引用", 2));
            for (int i = 0; i < references.size(); i++) {
                JSONObject ref = toObject(references.get(i), "来源 " + (i + 1));
                body.append(docxParagraph((i + 1) + ". " + firstNonBlank(ref.getStr("title", ""), ref.getStr("link", "")) + " " + ref.getStr("link", "")));
            }
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        putText(entries, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="svg" ContentType="image/svg+xml"/>
                  <Default Extension="png" ContentType="image/png"/>
                  <Default Extension="jpg" ContentType="image/jpeg"/>
                  <Default Extension="jpeg" ContentType="image/jpeg"/>
                  <Default Extension="gif" ContentType="image/gif"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>
                """);
        putText(entries, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """);
        putText(entries, "word/_rels/document.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                  %s
                </Relationships>
                """.formatted(String.join("\n  ", docx.imageRelationships)));
        putText(entries, "word/styles.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Heading1">
                    <w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
                    <w:pPr><w:spacing w:before="360" w:after="180"/></w:pPr>
                    <w:rPr><w:b/><w:color w:val="1E3A8A"/><w:sz w:val="36"/></w:rPr>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Heading2">
                    <w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
                    <w:pPr><w:spacing w:before="300" w:after="140"/></w:pPr>
                    <w:rPr><w:b/><w:color w:val="1E3A8A"/><w:sz w:val="30"/></w:rPr>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Heading3">
                    <w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
                    <w:pPr><w:spacing w:before="220" w:after="100"/></w:pPr>
                    <w:rPr><w:b/><w:color w:val="334155"/><w:sz w:val="26"/></w:rPr>
                  </w:style>
                </w:styles>
                """);
        putText(entries, "word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                    xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <w:body>%s<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body>
                </w:document>
                """.formatted(body));
        entries.putAll(docx.mediaEntries);
        writeZip(docxPath, entries);
    }

    private String chartImagePath(JSONObject chart) {
        String path = firstNonBlank(chart.getStr("path", ""),
                firstNonBlank(chart.getStr("svgPath", ""),
                        firstNonBlank(chart.getStr("imagePath", ""),
                                firstNonBlank(chart.getStr("image", ""), chart.getStr("url", "")))));
        if (StrUtil.isNotBlank(path)) {
            return path;
        }
        String previewPath = chart.getStr("previewPath", "");
        return isSupportedImagePath(previewPath) ? previewPath : "";
    }

    private String markdownImageUri(String imagePath) {
        if (StrUtil.isBlank(imagePath)) {
            return "";
        }
        String trimmed = imagePath.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        if (isHttpUrl(trimmed)) {
            try {
                ImageResource resource = resolveImageResource(trimmed);
                if (resource == null || resource.bytes().length == 0) {
                    return "";
                }
                String mime = imageMimeTypeFromExtension(resource.extension());
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(resource.bytes());
            } catch (Exception ignored) {
                return "";
            }
        }
        try {
            Path path = Paths.get(trimmed).toAbsolutePath().normalize();
            if (!Files.exists(path) || !Files.isRegularFile(path) || !isSupportedImagePath(path.toString())) {
                return trimmed;
            }
            String mime = imageMimeType(path.toString());
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            return "data:" + mime + ";base64," + encoded;
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private String htmlImageSrc(String imagePath) {
        if (StrUtil.isBlank(imagePath)) {
            return "";
        }
        String trimmed = imagePath.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        if (isHttpUrl(trimmed)) {
            return markdownImageUri(trimmed);
        }
        String embedded = markdownImageUri(trimmed);
        return embedded.startsWith("data:") ? embedded : "";
    }

    private String escapeMarkdownAlt(String text) {
        return defaultIfBlank(text, "")
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private void appendMarkdownImages(StringBuilder md, JSONArray images) {
        JSONArray normalized = normalizeImages(images);
        if (normalized.isEmpty()) {
            return;
        }
        md.append("## 相关图片\n\n");
        for (int i = 0; i < normalized.size(); i++) {
            JSONObject image = normalized.getJSONObject(i);
            String title = image.getStr("title", "相关图片 " + (i + 1));
            String imageUrl = image.getStr("image", "");
            String imageUri = markdownImageUri(imageUrl);
            if (StrUtil.isBlank(imageUri)) {
                continue;
            }
            md.append("### ").append(title).append("\n\n");
            md.append("![").append(escapeMarkdownAlt(title)).append("](").append(imageUri).append(")\n\n");
            String description = firstNonBlank(image.getStr("description", ""), image.getStr("source", ""));
            if (StrUtil.isNotBlank(description)) {
                md.append(description).append("\n\n");
            }
        }
    }

    private void appendHtmlImages(StringBuilder html, JSONArray images) {
        JSONArray normalized = normalizeImages(images);
        if (normalized.isEmpty()) {
            return;
        }
        html.append("<section><h2>相关图片</h2><div class=\"image-grid\">");
        for (int i = 0; i < normalized.size(); i++) {
            JSONObject image = normalized.getJSONObject(i);
            String title = image.getStr("title", "相关图片 " + (i + 1));
            String imageSrc = htmlImageSrc(image.getStr("image", ""));
            if (StrUtil.isBlank(imageSrc)) {
                continue;
            }
            html.append("<figure class=\"report-image\"><img src=\"").append(escapeAttr(imageSrc))
                    .append("\" alt=\"").append(escapeAttr(title)).append("\"><figcaption>")
                    .append(escape(title));
            String description = firstNonBlank(image.getStr("description", ""), image.getStr("source", ""));
            if (StrUtil.isNotBlank(description)) {
                html.append("<br>").append(escape(description));
            }
            html.append("</figcaption></figure>");
        }
        html.append("</div></section>");
    }

    private void addPdfReportImages(Document document, PdfDocument pdf, JSONArray images) {
        JSONArray normalized = normalizeImages(images);
        if (normalized.isEmpty()) {
            return;
        }
        addPdfHeading(document, "相关图片", 1);
        for (int i = 0; i < normalized.size(); i++) {
            JSONObject image = normalized.getJSONObject(i);
            String title = image.getStr("title", "相关图片 " + (i + 1));
            addPdfHeading(document, title, 2);
            addPdfImage(document, pdf, image.getStr("image", ""));
            String description = firstNonBlank(image.getStr("description", ""), image.getStr("source", ""));
            if (StrUtil.isNotBlank(description)) {
                addMarkdownToPdf(document, description);
            }
        }
    }

    private void appendDocxReportImages(DocxContext docx, JSONArray images) {
        JSONArray normalized = normalizeImages(images);
        if (normalized.isEmpty()) {
            return;
        }
        docx.body.append(docxHeading("相关图片", 2));
        for (int i = 0; i < normalized.size(); i++) {
            JSONObject image = normalized.getJSONObject(i);
            String title = image.getStr("title", "相关图片 " + (i + 1));
            docx.body.append(docxHeading(title, 3));
            appendDocxImage(docx, image.getStr("image", ""), title);
            String description = firstNonBlank(image.getStr("description", ""), image.getStr("source", ""));
            if (StrUtil.isNotBlank(description)) {
                appendDocxMarkdown(docx.body, description);
            }
        }
    }

    private JSONArray normalizeImages(JSONArray images) {
        JSONArray normalized = JSONUtil.createArray();
        if (images == null || images.isEmpty()) {
            return normalized;
        }
        for (int i = 0; i < images.size(); i++) {
            JSONObject source = toObject(images.get(i), "相关图片 " + (i + 1));
            String imageUrl = firstImageField(source,
                    "localPath", "path", "imagePath", "image", "url", "link", "thumbnail", "thumb", "pic",
                    "objURL", "objurl", "middleURL", "middleurl", "thumbURL", "thumburl");
            if (StrUtil.isBlank(imageUrl)) {
                continue;
            }
            JSONObject image = JSONUtil.createObj();
            image.set("title", firstNonBlank(source.getStr("title", ""), "相关图片 " + (normalized.size() + 1)));
            image.set("image", imageUrl);
            image.set("thumbnail", firstNonBlank(source.getStr("thumbnail", ""), imageUrl));
            image.set("description", firstNonBlank(source.getStr("description", ""), source.getStr("insight", "")));
            image.set("source", source.getStr("source", ""));
            normalized.add(image);
        }
        return normalized;
    }

    private JSONArray discoverSessionImages() {
        JSONArray images = JSONUtil.createArray();
        Path imageDir = GeneratedFileContext.baseDir().resolve("image").toAbsolutePath().normalize();
        if (!Files.exists(imageDir) || !Files.isDirectory(imageDir)) {
            return images;
        }
        try (var stream = Files.list(imageDir)) {
            List<Path> paths = stream
                    .filter(path -> Files.isRegularFile(path) && isSupportedImagePath(path.toString()))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .limit(4)
                    .toList();
            for (int i = 0; i < paths.size(); i++) {
                JSONObject image = JSONUtil.createObj();
                image.set("title", "相关图片 " + (i + 1));
                image.set("image", paths.get(i).toString());
                image.set("localPath", paths.get(i).toString());
                image.set("description", "智能体图片搜索结果");
                image.set("source", "searchImages");
                images.add(image);
            }
        } catch (Exception e) {
            log.debug("Failed to discover session images for report", e);
        }
        return images;
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String firstImageField(JSONObject source, String... keys) {
        if (source == null) {
            return "";
        }
        for (String key : keys) {
            String value = source.getStr(key, "");
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String buildMarkdownTable(JSONArray rows) {
        JSONArray normalizedRows = normalizeRows(rows);
        Set<String> keys = collectKeys(normalizedRows);
        StringBuilder md = new StringBuilder();
        md.append("| ");
        keys.forEach(key -> md.append(escapeMarkdownCell(key)).append(" | "));
        md.append("\n| ");
        keys.forEach(key -> md.append("--- | "));
        md.append("\n");
        for (int i = 0; i < normalizedRows.size(); i++) {
            JSONObject row = toObject(normalizedRows.get(i), "Row " + (i + 1));
            md.append("| ");
            keys.forEach(key -> md.append(escapeMarkdownCell(String.valueOf(defaultIfNull(row.get(key), "")))).append(" | "));
            md.append("\n");
        }
        return md.toString();
    }

    private String escapeMarkdownCell(String text) {
        return defaultIfBlank(text, "")
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");
    }

    private void addPdfChartImage(Document document, PdfDocument pdf, String imagePath) {
        addPdfImage(document, pdf, imagePath);
    }

    private void addPdfImage(Document document, PdfDocument pdf, String imagePath) {
        if (StrUtil.isBlank(imagePath)) {
            return;
        }
        try {
            Image image;
            if (isHttpUrl(imagePath)) {
                ImageResource imageResource = resolveImageResource(imagePath);
                if (imageResource == null || imageResource.bytes().length == 0) {
                    return;
                }
                if ("svg".equals(imageResource.extension())) {
                    try (var input = new ByteArrayInputStream(imageResource.bytes())) {
                        image = SvgConverter.convertToImage(input, pdf);
                    }
                } else {
                    image = new Image(ImageDataFactory.create(imageResource.bytes()));
                }
            } else {
                Path path = Paths.get(imagePath).toAbsolutePath().normalize();
                if (!Files.exists(path) || !Files.isRegularFile(path) || !isSupportedImagePath(path.toString())) {
                    return;
                }
                if (isSvgPath(path.toString())) {
                    try (var input = Files.newInputStream(path)) {
                        image = SvgConverter.convertToImage(input, pdf);
                    }
                } else {
                    image = new Image(ImageDataFactory.create(path.toString()));
                }
            }
            image.scaleToFit(500, 280);
            image.setMarginBottom(10);
            document.add(image);
        } catch (Exception e) {
            document.add(new Paragraph("图片暂无法嵌入。")
                    .setFontSize(9)
                    .setFontColor(new DeviceRgb(100, 116, 139)));
        }
    }

    private void addPdfTable(Document document, JSONArray rows) {
        JSONArray normalizedRows = normalizeRows(rows);
        Set<String> keys = collectKeys(normalizedRows);
        if (normalizedRows.isEmpty() || keys.isEmpty()) {
            return;
        }
        Table table = new Table(UnitValue.createPercentArray(keys.size())).useAllAvailableWidth();
        for (String key : keys) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(pdfSafeText(key)).setFontSize(9))
                    .setBackgroundColor(new DeviceRgb(238, 242, 255))
                    .setFontColor(new DeviceRgb(49, 46, 129)));
        }
        for (int i = 0; i < normalizedRows.size(); i++) {
            JSONObject row = toObject(normalizedRows.get(i), "Row " + (i + 1));
            for (String key : keys) {
                table.addCell(new Cell()
                        .add(new Paragraph(pdfSafeText(String.valueOf(defaultIfNull(row.get(key), "")))).setFontSize(9))
                        .setFontColor(new DeviceRgb(30, 41, 59)));
            }
        }
        document.add(table.setMarginBottom(12));
    }

    private void appendDocxImage(DocxContext docx, String imagePath, String altText) {
        if (StrUtil.isBlank(imagePath)) {
            return;
        }
        try {
            ImageResource imageResource = resolveImageResource(imagePath);
            if (imageResource == null || imageResource.bytes().length == 0) {
                return;
            }
            String extension = imageResource.extension();
            int imageId = docx.nextImageId++;
            String relId = "rIdImage" + imageId;
            String mediaName = "image" + imageId + "." + extension;
            docx.mediaEntries.put("word/media/" + mediaName, imageResource.bytes());
            docx.imageRelationships.add("<Relationship Id=\"" + relId
                    + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/"
                    + mediaName + "\"/>");
            docx.body.append(docxImage(relId, mediaName, altText, imageId));
        } catch (Exception e) {
            docx.body.append(docxParagraph("图片暂无法嵌入。"));
        }
    }

    private ImageResource resolveImageResource(String imagePath) throws Exception {
        if (isHttpUrl(imagePath)) {
            return downloadImageResource(imagePath);
        }
        Path path = Paths.get(imagePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path) || !isSupportedImagePath(path.toString())) {
            return null;
        }
        return new ImageResource(Files.readAllBytes(path), imageExtension(path.toString()));
    }

    private ImageResource downloadImageResource(String imageUrl) throws Exception {
        URI safeUri = UrlSafety.requireSafeHttpUrl(imageUrl);
        HttpResponse<byte[]> response = sendImageRequestFollowingSafeRedirects(safeUri, 3);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_REPORT_IMAGE_BYTES) {
            return null;
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        String extension = imageExtensionFromContentType(contentType);
        if (StrUtil.isBlank(extension)) {
            extension = firstNonBlank(imageExtensionFromBytes(bytes), imageExtension(imageUrl));
        }
        if (StrUtil.isBlank(extension) || "webp".equals(extension)) {
            return null;
        }
        return new ImageResource(bytes, extension);
    }

    private HttpResponse<byte[]> sendImageRequestFollowingSafeRedirects(URI startUri, int maxRedirects) throws Exception {
        URI current = startUri;
        for (int i = 0; i <= maxRedirects; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AI-Agent-Report/1.0)")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = IMAGE_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (!isRedirectStatus(response.statusCode())) {
                return response;
            }
            String location = response.headers().firstValue("location").orElse("");
            if (StrUtil.isBlank(location) || i == maxRedirects) {
                return response;
            }
            URI next = current.resolve(location).normalize();
            current = UrlSafety.requireSafeHttpUrl(next.toString());
        }
        throw new IllegalStateException("Image redirect limit exceeded");
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String docxImage(String relId, String mediaName, String altText, int imageId) {
        long width = 5486400L;
        long height = 3086100L;
        String escapedAlt = escapeXml(defaultIfBlank(altText, mediaName));
        return """
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:inline distT="0" distB="0" distL="0" distR="0">
                        <wp:extent cx="%d" cy="%d"/>
                        <wp:effectExtent l="0" t="0" r="0" b="0"/>
                        <wp:docPr id="%d" name="%s" descr="%s"/>
                        <wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
                        <a:graphic>
                          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                            <pic:pic>
                              <pic:nvPicPr><pic:cNvPr id="%d" name="%s"/><pic:cNvPicPr/></pic:nvPicPr>
                              <pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                              <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                            </pic:pic>
                          </a:graphicData>
                        </a:graphic>
                      </wp:inline>
                    </w:drawing>
                  </w:r>
                </w:p>
                """.formatted(width, height, imageId, escapeXml(mediaName), escapedAlt, imageId, escapeXml(mediaName), relId, width, height);
    }

    private String docxTable(JSONArray rows) {
        JSONArray normalizedRows = normalizeRows(rows);
        Set<String> keys = collectKeys(normalizedRows);
        if (normalizedRows.isEmpty() || keys.isEmpty()) {
            return "";
        }
        int width = Math.max(1200, 9000 / Math.max(keys.size(), 1));
        StringBuilder table = new StringBuilder();
        table.append("""
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="0" w:type="auto"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                      <w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                      <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                      <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                    </w:tblBorders>
                  </w:tblPr>
                """);
        table.append("<w:tr>");
        for (String key : keys) {
            table.append(docxTableCell(key, width, true));
        }
        table.append("</w:tr>");
        for (int i = 0; i < normalizedRows.size(); i++) {
            JSONObject row = toObject(normalizedRows.get(i), "Row " + (i + 1));
            table.append("<w:tr>");
            for (String key : keys) {
                table.append(docxTableCell(String.valueOf(defaultIfNull(row.get(key), "")), width, false));
            }
            table.append("</w:tr>");
        }
        table.append("</w:tbl>");
        return table.toString();
    }

    private String docxTableCell(String text, int width, boolean header) {
        String shading = header ? "<w:shd w:fill=\"EEF2FF\"/>" : "";
        String runPr = header ? "<w:rPr><w:b/><w:color w:val=\"312E81\"/></w:rPr>" : "";
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>" + shading
                + "</w:tcPr><w:p><w:r>" + runPr + "<w:t xml:space=\"preserve\">"
                + escapeXml(text) + "</w:t></w:r></w:p></w:tc>";
    }

    private Set<String> collectKeys(JSONArray rows) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; rows != null && i < rows.size(); i++) {
            keys.addAll(toObject(rows.get(i), "Row " + (i + 1)).keySet());
        }
        if (keys.isEmpty()) {
            keys.add("name");
            keys.add("value");
        }
        return keys;
    }

    private JSONArray parseArray(String json) {
        if (StrUtil.isBlank(json)) {
            return JSONUtil.createArray();
        }
        try {
            Object parsed = JSONUtil.parse(json);
            if (parsed instanceof JSONArray array) {
                return array;
            }
            if (parsed instanceof JSONObject object) {
                JSONArray array = JSONUtil.createArray();
                array.add(object);
                return array;
            }
        } catch (Exception ignored) {
        }
        return JSONUtil.createArray();
    }

    private JSONArray parseRows(String json) {
        if (StrUtil.isBlank(json)) {
            return JSONUtil.createArray();
        }
        try {
            Object parsed = JSONUtil.parse(json);
            if (parsed instanceof JSONArray array) {
                JSONArray tableObjects = normalizeTableObjects(array);
                if (!tableObjects.isEmpty()) {
                    return tableObjects;
                }
                return normalizeRows(array);
            }
            if (parsed instanceof JSONObject object) {
                JSONArray tableRows = normalizeTableObject(object);
                if (!tableRows.isEmpty()) {
                    return tableRows;
                }
                Object rows = firstNonNull(object.get("rows"), object.get("data"), object.get("items"));
                if (rows instanceof JSONArray array) {
                    return normalizeRows(array);
                }
                JSONArray array = JSONUtil.createArray();
                array.add(object);
                return normalizeRows(array);
            }
        } catch (Exception ignored) {
        }
        return JSONUtil.createArray();
    }

    private JSONArray normalizeTableObjects(JSONArray array) {
        JSONArray combined = JSONUtil.createArray();
        boolean foundTableObject = false;
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            if (!(value instanceof JSONObject object)) {
                continue;
            }
            JSONArray rows = normalizeTableObject(object);
            if (!rows.isEmpty()) {
                foundTableObject = true;
                for (Object row : rows) {
                    combined.add(row);
                }
            }
        }
        return foundTableObject ? combined : JSONUtil.createArray();
    }

    private JSONArray normalizeTableObject(JSONObject object) {
        Object rows = firstNonNull(object.get("rows"), object.get("data"), object.get("items"));
        if (!(rows instanceof JSONArray rowArray)) {
            return JSONUtil.createArray();
        }
        Object headers = firstNonNull(object.get("headers"), object.get("columns"), object.get("header"));
        if (headers instanceof JSONArray headerArray) {
            return normalizeRowsWithHeaders(headerArray, rowArray);
        }
        return normalizeRows(rowArray);
    }

    private JSONArray normalizeRows(JSONArray rows) {
        JSONArray normalized = JSONUtil.createArray();
        if (rows == null || rows.isEmpty()) {
            return normalized;
        }

        Object first = rows.get(0);
        if (first instanceof JSONArray headersRow) {
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headersRow.size(); i++) {
                headers.add(defaultIfBlank(String.valueOf(defaultIfNull(headersRow.get(i), "")), "列" + (i + 1)));
            }
            for (int i = 1; i < rows.size(); i++) {
                Object value = rows.get(i);
                JSONObject row = JSONUtil.createObj();
                if (value instanceof JSONArray values) {
                    int columnCount = Math.max(headers.size(), values.size());
                    for (int column = 0; column < columnCount; column++) {
                        String key = column < headers.size() ? headers.get(column) : "列" + (column + 1);
                        row.set(key, column < values.size() ? values.get(column) : "");
                    }
                } else if (value instanceof JSONObject object) {
                    row.putAll(object);
                } else {
                    row.set(headers.isEmpty() ? "value" : headers.get(0), defaultIfNull(value, ""));
                }
                normalized.add(row);
            }
            return normalized;
        }

        for (int i = 0; i < rows.size(); i++) {
            Object value = rows.get(i);
            if (value instanceof JSONObject object) {
                Object nestedRows = firstNonNull(object.get("rows"), object.get("data"), object.get("items"));
                if (object.size() == 1 && nestedRows instanceof JSONArray array) {
                    return normalizeRows(array);
                }
                normalized.add(object);
            } else if (value instanceof JSONArray array) {
                JSONObject row = JSONUtil.createObj();
                for (int column = 0; column < array.size(); column++) {
                    row.set("列" + (column + 1), defaultIfNull(array.get(column), ""));
                }
                normalized.add(row);
            } else {
                JSONObject row = JSONUtil.createObj();
                row.set("value", defaultIfNull(value, ""));
                normalized.add(row);
            }
        }
        return normalized;
    }

    private JSONArray normalizeRowsWithHeaders(JSONArray headersArray, JSONArray rows) {
        JSONArray normalized = JSONUtil.createArray();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headersArray.size(); i++) {
            headers.add(defaultIfBlank(String.valueOf(defaultIfNull(headersArray.get(i), "")), "列" + (i + 1)));
        }
        for (int i = 0; rows != null && i < rows.size(); i++) {
            Object value = rows.get(i);
            JSONObject row = JSONUtil.createObj();
            if (value instanceof JSONArray values) {
                int columnCount = Math.max(headers.size(), values.size());
                for (int column = 0; column < columnCount; column++) {
                    String key = column < headers.size() ? headers.get(column) : "列" + (column + 1);
                    row.set(key, column < values.size() ? values.get(column) : "");
                }
            } else if (value instanceof JSONObject object) {
                row.putAll(object);
            } else {
                row.set(headers.isEmpty() ? "value" : headers.get(0), defaultIfNull(value, ""));
            }
            normalized.add(row);
        }
        return normalized;
    }

    private JSONObject toObject(Object value, String fallbackTitle) {
        if (value instanceof JSONObject object) {
            return object;
        }
        JSONObject object = JSONUtil.createObj();
        object.set("title", fallbackTitle);
        object.set("content", value == null ? "" : String.valueOf(value));
        return object;
    }

    private JSONArray files(JSONObject... files) {
        JSONArray array = JSONUtil.createArray();
        for (JSONObject file : files) {
            array.add(file);
        }
        return array;
    }

    private JSONObject file(String label, String type, String path, boolean previewable) {
        JSONObject file = JSONUtil.createObj();
        file.set("label", label);
        file.set("type", type);
        file.set("path", path);
        file.set("previewable", previewable);
        return file;
    }

    private void putText(Map<String, byte[]> entries, String name, String content) {
        entries.put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(target))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private boolean isHttpUrl(String value) {
        String lower = defaultIfBlank(value, "").toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isSupportedImagePath(String value) {
        String extension = imageExtension(value);
        return "svg".equals(extension) || "png".equals(extension) || "jpg".equals(extension)
                || "jpeg".equals(extension) || "gif".equals(extension);
    }

    private boolean isSvgPath(String value) {
        return "svg".equals(imageExtension(value));
    }

    private String imageMimeType(String value) {
        return imageMimeTypeFromExtension(imageExtension(value));
    }

    private String imageMimeTypeFromExtension(String extension) {
        return switch (defaultIfBlank(extension, "")) {
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    private String imageExtensionFromContentType(String contentType) {
        String normalized = defaultIfBlank(contentType, "").toLowerCase();
        if (normalized.contains("image/png")) {
            return "png";
        }
        if (normalized.contains("image/jpeg") || normalized.contains("image/jpg")) {
            return "jpg";
        }
        if (normalized.contains("image/gif")) {
            return "gif";
        }
        if (normalized.contains("image/svg")) {
            return "svg";
        }
        if (normalized.contains("image/webp")) {
            return "webp";
        }
        return "";
    }

    private String imageExtensionFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "";
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "png";
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return "jpg";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "gif";
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.UTF_8).trim().toLowerCase();
        if (prefix.startsWith("<svg") || prefix.contains("<svg")) {
            return "svg";
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "webp";
        }
        return "";
    }

    private String imageExtension(String value) {
        String clean = defaultIfBlank(value, "").trim().toLowerCase();
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            clean = clean.substring(0, queryIndex);
        }
        int fragmentIndex = clean.indexOf('#');
        if (fragmentIndex >= 0) {
            clean = clean.substring(0, fragmentIndex);
        }
        int dotIndex = clean.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == clean.length() - 1) {
            return "";
        }
        String extension = clean.substring(dotIndex + 1);
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private String docxParagraph(String text) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + escapeXml(stripInlineMarkdown(stripMarkdownHeading(text))) + "</w:t></w:r></w:p>";
    }

    private String docxHeading(String text, int level) {
        int normalizedLevel = Math.max(1, Math.min(level, 3));
        int size = normalizedLevel == 1 ? 36 : normalizedLevel == 2 ? 30 : 26;
        return "<w:p><w:pPr><w:pStyle w:val=\"Heading" + normalizedLevel + "\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\""
                + size + "\"/></w:rPr><w:t xml:space=\"preserve\">" + escapeXml(stripInlineMarkdown(stripMarkdownHeading(text))) + "</w:t></w:r></w:p>";
    }

    private void appendDocxMarkdown(StringBuilder body, String markdown) {
        String[] lines = defaultIfBlank(markdown, "").replace("\r\n", "\n").split("\n+");
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            int level = markdownHeadingLevel(line);
            if (level > 0) {
                body.append(docxHeading(stripMarkdownHeading(line), level));
            } else {
                body.append(docxParagraph(stripMarkdownHeading(line)));
            }
        }
    }

    private void addMarkdownToPdf(Document document, String markdown) {
        String[] lines = defaultIfBlank(markdown, "").replace("\r\n", "\n").split("\n+");
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            int level = markdownHeadingLevel(line);
            if (level > 0) {
                addPdfHeading(document, stripMarkdownHeading(line), Math.min(level, 3));
                continue;
            }
            String paragraphText = stripMarkdownHeading(line).replaceFirst("^\\s*[-*]\\s+", "");
            document.add(pdfParagraphFromMarkdown(paragraphText)
                    .setFontSize(11)
                    .setFontColor(new DeviceRgb(30, 41, 59))
                    .setMarginBottom(6));
        }
    }

    private Paragraph pdfParagraphFromMarkdown(String markdown) {
        Paragraph paragraph = new Paragraph();
        String value = defaultIfBlank(markdown, "");
        Matcher matcher = BOLD_PATTERN.matcher(value);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                paragraph.add(new Text(pdfSafeText(value.substring(cursor, matcher.start()))));
            }
            paragraph.add(new Text(pdfSafeText(matcher.group(1))));
            cursor = matcher.end();
        }
        if (cursor < value.length()) {
            paragraph.add(new Text(pdfSafeText(value.substring(cursor))));
        }
        if (paragraph.getChildren().isEmpty()) {
            paragraph.add(new Text(pdfSafeText(stripInlineMarkdown(value))));
        }
        return paragraph;
    }

    private void addPdfHeading(Document document, String text, int level) {
        int size = level <= 1 ? 16 : level == 2 ? 13 : 12;
        document.add(new Paragraph(pdfSafeText(stripMarkdownHeading(text)))
                .setFontSize(size)
                .setFontColor(new DeviceRgb(30, 58, 138))
                .setMarginTop(level <= 1 ? 18 : 10)
                .setMarginBottom(8));
    }

    private String pdfSafeText(String text) {
        String value = defaultIfBlank(text, "");
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (Character.isSupplementaryCodePoint(codePoint)) {
                return;
            }
            if (Character.getType(codePoint) == Character.OTHER_SYMBOL) {
                return;
            }
            if (codePoint == 0xFE0E || codePoint == 0xFE0F || codePoint == 0x200D) {
                return;
            }
            safe.appendCodePoint(codePoint);
        });
        return safe.toString();
    }

    private String markdownToHtml(String markdown) {
        String[] lines = defaultIfBlank(markdown, "").replace("\r\n", "\n").split("\n+");
        StringBuilder html = new StringBuilder();
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            int level = markdownHeadingLevel(line);
            if (level > 0) {
                int htmlLevel = Math.min(level + 1, 4);
                html.append("<h").append(htmlLevel).append(">")
                        .append(escape(stripMarkdownHeading(line)))
                        .append("</h").append(htmlLevel).append(">");
            } else {
                html.append("<p>").append(inlineMarkdownToHtml(line.replaceFirst("^\\s*[-*]\\s+", ""))).append("</p>");
            }
        }
        return html.toString();
    }

    private String inlineMarkdownToHtml(String text) {
        String escaped = escape(stripMarkdownHeading(text));
        escaped = BOLD_PATTERN.matcher(escaped).replaceAll("<strong>$1</strong>");
        escaped = escaped.replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "<em>$1</em>");
        return escaped;
    }

    private int markdownHeadingLevel(String text) {
        String value = defaultIfBlank(text, "").trim();
        int count = 0;
        while (count < value.length() && value.charAt(count) == '#') {
            count++;
        }
        if (count > 0 && count < value.length() && Character.isWhitespace(value.charAt(count))) {
            return count;
        }
        return 0;
    }

    private String stripMarkdownHeading(String text) {
        return defaultIfBlank(text, "")
                .replaceFirst("^\\s*#{1,6}\\s+", "")
                .replaceFirst("^\\s*[-*]\\s+", "")
                .trim();
    }

    private String stripInlineMarkdown(String text) {
        return defaultIfBlank(text, "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "$1")
                .replaceAll("`([^`\\n]+)`", "$1")
                .trim();
    }

    private String normalizeBaseName(String fileName) {
        String fallback = "agent_report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String name = defaultIfBlank(fileName, fallback).trim();
        name = name.replaceAll("\\.(md|pdf|html|htm|docx)$", "");
        name = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return StrUtil.isBlank(name) ? fallback : name;
    }

    private Object defaultIfNull(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private Object firstNonNull(Object first, Object second, Object third) {
        if (first != null) return first;
        if (second != null) return second;
        return third;
    }

    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : defaultIfBlank(second, "");
    }

    private String defaultIfBlank(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value;
    }

    private String escapeXml(String text) {
        return escape(text).replace("'", "&apos;");
    }

    private String escape(String text) {
        return defaultIfBlank(text, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeAttr(String text) {
        return escape(text).replace("'", "&#39;");
    }

    private static class DocxContext {
        private final StringBuilder body = new StringBuilder();
        private final Map<String, byte[]> mediaEntries = new LinkedHashMap<>();
        private final List<String> imageRelationships = new ArrayList<>();
        private int nextImageId = 1;
    }

    private record ImageResource(byte[] bytes, String extension) {
    }
}
