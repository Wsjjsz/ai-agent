package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiagent.config.UrlSafety;
import com.aiagent.files.GeneratedFileContext;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 图文报告 PDF 生成工具，同时生成 HTML 预览文件。
 */
public class RichPDFGenerationTool {

    private static final int MAX_NEWS_ITEMS = 6;
    private static final int MAX_QUOTES = 8;
    private static final int MAX_REFERENCES = 12;

    @Tool(description = "Generate a rich financial PDF report with cover, summary cards, news images, market quote table, references, risk page, and HTML preview", returnDirect = false)
    public String generateRichPDF(
            @ToolParam(description = "PDF file name, for example: gold-investment-report.pdf") String fileName,
            @ToolParam(description = "Report title") String title,
            @ToolParam(description = "Report subtitle") String subtitle,
            @ToolParam(description = "Executive summary in Markdown or plain text") String executiveSummary,
            @ToolParam(description = "JSON array of news items with title, snippet, link, image, source, publishedAt") String newsItemsJson,
            @ToolParam(description = "JSON array of market quotes with symbol, price, change, changePercent, date, time") String marketQuotesJson,
            @ToolParam(description = "Risk tips in Markdown or plain text") String riskTips,
            @ToolParam(description = "JSON array of references with title and link") String referencesJson) {
        try {
            String safeFileName = normalizePdfFileName(fileName);
            String htmlFileName = safeFileName.replaceFirst("\\.pdf$", ".html");
            Path pdfPath = GeneratedFileContext.resolve("pdf", safeFileName);
            Path previewPath = GeneratedFileContext.resolve("preview", htmlFileName);

            FileUtil.mkdir(pdfPath.getParent().toString());
            FileUtil.mkdir(previewPath.getParent().toString());

            JSONArray newsItems = parseArray(newsItemsJson);
            JSONArray marketQuotes = parseArray(marketQuotesJson);
            JSONArray references = parseArray(referencesJson);

            writePdf(pdfPath, title, subtitle, executiveSummary, newsItems, marketQuotes, riskTips, references);
            FileUtil.writeString(buildPreviewHtml(title, subtitle, executiveSummary, newsItems, marketQuotes, riskTips, references),
                    previewPath.toFile(), StandardCharsets.UTF_8);

            JSONObject result = JSONUtil.createObj();
            result.set("success", true);
            result.set("pdfPath", pdfPath.toString());
            result.set("previewPath", previewPath.toString());
            JSONArray files = JSONUtil.createArray();
            files.add(file("PDF 正式报告", "pdf", pdfPath.toString(), true));
            files.add(file("HTML 预览", "html", previewPath.toString(), true));
            result.set("files", files);
            result.set("message", "Rich PDF generated successfully to: " + pdfPath + "\nPreview generated to: " + previewPath);
            result.set("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return result.toString();
        } catch (Exception e) {
            JSONObject error = JSONUtil.createObj();
            error.set("success", false);
            error.set("code", "RICH_PDF_GENERATION_FAILED");
            error.set("message", e.getMessage());
            return error.toString();
        }
    }

    private void writePdf(Path pdfPath, String title, String subtitle, String executiveSummary,
                          JSONArray newsItems, JSONArray marketQuotes, String riskTips, JSONArray references) throws Exception {
        try (PdfWriter writer = new PdfWriter(pdfPath.toFile());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            document.setFont(font);

            addCover(document, title, subtitle);
            addSummary(document, executiveSummary);
            addNews(document, newsItems);
            addMarketQuotes(document, marketQuotes);
            addReferences(document, references);
            addRiskPage(document, riskTips);
        }
    }

    private void addCover(Document document, String title, String subtitle) {
        document.add(new Paragraph(defaultIfBlank(title, "金融投资研究报告"))
                .setFontSize(26)
                .setFontColor(new DeviceRgb(36, 52, 88))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(120));
        document.add(new Paragraph(defaultIfBlank(subtitle, "搜索、行情与风险提示综合分析"))
                .setFontSize(14)
                .setFontColor(new DeviceRgb(93, 108, 132))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(14));
        document.add(new Paragraph(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .setFontSize(10)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(40));
        document.add(new AreaBreak());
    }

    private void addSummary(Document document, String executiveSummary) {
        document.add(sectionTitle("摘要卡片"));
        String[] cards = splitLines(defaultIfBlank(executiveSummary, "暂无摘要。"));
        for (String card : cards) {
            if (StrUtil.isBlank(card)) {
                continue;
            }
            document.add(new Paragraph(cleanMarkdownMarker(card))
                    .setFontSize(11)
                    .setPadding(10)
                    .setBorder(new SolidBorder(new DeviceRgb(219, 226, 238), 1))
                    .setBackgroundColor(new DeviceRgb(248, 250, 252)));
        }
    }

    private void addNews(Document document, JSONArray newsItems) {
        document.add(sectionTitle("最新新闻与图文素材"));
        if (newsItems == null || newsItems.isEmpty()) {
            document.add(new Paragraph("暂无新闻素材。").setFontSize(11));
            return;
        }
        int count = Math.min(MAX_NEWS_ITEMS, newsItems.size());
        for (int i = 0; i < count; i++) {
            JSONObject item = newsItems.getJSONObject(i);
            String imageUrl = item.getStr("image", "");
            addRemoteImage(document, imageUrl);
            document.add(new Paragraph((i + 1) + ". " + item.getStr("title", ""))
                    .setFontSize(12)
                    .setFontColor(new DeviceRgb(36, 52, 88)));
            document.add(new Paragraph(item.getStr("snippet", ""))
                    .setFontSize(10)
                    .setFontColor(new DeviceRgb(71, 85, 105)));
            String source = item.getStr("source", "");
            String link = item.getStr("link", "");
            document.add(new Paragraph(source + (StrUtil.isBlank(link) ? "" : "  " + link))
                    .setFontSize(8)
                    .setFontColor(ColorConstants.GRAY));
        }
    }

    private void addMarketQuotes(Document document, JSONArray marketQuotes) {
        document.add(sectionTitle("行情表格"));
        if (marketQuotes == null || marketQuotes.isEmpty()) {
            document.add(new Paragraph("暂无行情数据。").setFontSize(11));
            return;
        }
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 2, 2}))
                .useAllAvailableWidth();
        addHeaderCell(table, "标的");
        addHeaderCell(table, "价格");
        addHeaderCell(table, "涨跌");
        addHeaderCell(table, "涨跌幅");
        addHeaderCell(table, "时间");
        int count = Math.min(MAX_QUOTES, marketQuotes.size());
        for (int i = 0; i < count; i++) {
            JSONObject quote = marketQuotes.getJSONObject(i);
            addBodyCell(table, firstNonBlank(quote.getStr("symbol", ""), quote.getStr("name", "")));
            addBodyCell(table, value(quote, "price"));
            addBodyCell(table, value(quote, "change"));
            addBodyCell(table, firstNonBlank(value(quote, "changePercent"), value(quote, "changePercent") + "%"));
            addBodyCell(table, quote.getStr("date", "") + " " + quote.getStr("time", ""));
        }
        document.add(table);
    }

    private void addReferences(Document document, JSONArray references) {
        document.add(sectionTitle("来源引用"));
        if (references == null || references.isEmpty()) {
            document.add(new Paragraph("暂无来源引用。").setFontSize(11));
            return;
        }
        int count = Math.min(MAX_REFERENCES, references.size());
        for (int i = 0; i < count; i++) {
            JSONObject reference = references.getJSONObject(i);
            String title = firstNonBlank(reference.getStr("title", ""), reference.getStr("link", ""));
            String link = reference.getStr("link", "");
            document.add(new Paragraph((i + 1) + ". " + title + (StrUtil.isBlank(link) ? "" : "\n" + link))
                    .setFontSize(9));
        }
    }

    private void addRiskPage(Document document, String riskTips) {
        document.add(new AreaBreak());
        document.add(sectionTitle("风险提示页"));
        String content = defaultIfBlank(riskTips, "市场有风险，投资需谨慎。本报告仅供信息参考，不构成任何投资建议。");
        for (String line : splitLines(content)) {
            if (StrUtil.isNotBlank(line)) {
                document.add(new Paragraph(cleanMarkdownMarker(line)).setFontSize(11));
            }
        }
    }

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text)
                .setFontSize(16)
                .setFontColor(new DeviceRgb(67, 56, 202))
                .setMarginTop(18);
    }

    private void addHeaderCell(Table table, String text) {
        table.addHeaderCell(new Cell().add(new Paragraph(text).setFontSize(10))
                .setBackgroundColor(new DeviceRgb(235, 239, 255)));
    }

    private void addBodyCell(Table table, String text) {
        table.addCell(new Cell().add(new Paragraph(defaultIfBlank(text, "-")).setFontSize(9)));
    }

    private void addRemoteImage(Document document, String imageUrl) {
        if (StrUtil.isBlank(imageUrl) || !imageUrl.startsWith("http")) {
            return;
        }
        try {
            Image image = new Image(ImageDataFactory.create(UrlSafety.requireSafeHttpUrl(imageUrl).toString()));
            image.setAutoScale(true);
            image.setMaxHeight(120);
            document.add(image);
        } catch (Exception ignored) {
            // 图片失败时不影响报告生成。
        }
    }

    private String buildPreviewHtml(String title, String subtitle, String executiveSummary,
                                    JSONArray newsItems, JSONArray marketQuotes, String riskTips, JSONArray references) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>报告预览</title>
                  <style>
                    body{margin:0;background:#edf1f7;color:#172033;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}
                    main{max-width:900px;margin:24px auto;background:#fff;box-shadow:0 10px 35px rgba(15,23,42,.12);}
                    .cover{padding:72px 56px;background:#172033;color:#fff;}
                    .cover h1{margin:0;font-size:34px;line-height:1.2;}
                    .cover p{color:#cbd5e1;font-size:15px;}
                    section{padding:30px 56px;border-bottom:1px solid #e5e7eb;}
                    h2{margin:0 0 16px;color:#4338ca;font-size:20px;}
                    .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;}
                    .card{background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:14px;}
                    .news{display:grid;grid-template-columns:140px 1fr;gap:16px;margin:16px 0;}
                    .news img{width:140px;height:88px;object-fit:cover;border-radius:6px;background:#e2e8f0;}
                    .news h3{margin:0 0 8px;font-size:16px;}
                    .muted{color:#64748b;font-size:12px;}
                    table{width:100%;border-collapse:collapse;}
                    th,td{border:1px solid #e2e8f0;padding:10px;text-align:left;font-size:13px;}
                    th{background:#eef2ff;color:#3730a3;}
                  </style>
                </head>
                <body><main>
                """);
        html.append("<div class=\"cover\"><h1>").append(escape(title)).append("</h1><p>")
                .append(escape(defaultIfBlank(subtitle, "搜索、行情与风险提示综合分析")))
                .append("</p><p>")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</p></div>");
        html.append("<section><h2>摘要卡片</h2><div class=\"cards\">");
        for (String line : splitLines(defaultIfBlank(executiveSummary, "暂无摘要。"))) {
            if (StrUtil.isNotBlank(line)) {
                html.append("<div class=\"card\">").append(escape(cleanMarkdownMarker(line))).append("</div>");
            }
        }
        html.append("</div></section>");
        html.append("<section><h2>最新新闻与图文素材</h2>");
        for (int i = 0; newsItems != null && i < Math.min(MAX_NEWS_ITEMS, newsItems.size()); i++) {
            JSONObject item = newsItems.getJSONObject(i);
            html.append("<div class=\"news\">");
            html.append("<img src=\"").append(escapeAttr(item.getStr("image", ""))).append("\" alt=\"\">");
            html.append("<div><h3>").append(escape(item.getStr("title", ""))).append("</h3><p>")
                    .append(escape(item.getStr("snippet", ""))).append("</p><p class=\"muted\">")
                    .append(escape(item.getStr("source", ""))).append(" ")
                    .append(escape(item.getStr("link", ""))).append("</p></div></div>");
        }
        html.append("</section><section><h2>行情表格</h2><table><thead><tr><th>标的</th><th>价格</th><th>涨跌</th><th>涨跌幅</th><th>时间</th></tr></thead><tbody>");
        for (int i = 0; marketQuotes != null && i < Math.min(MAX_QUOTES, marketQuotes.size()); i++) {
            JSONObject quote = marketQuotes.getJSONObject(i);
            html.append("<tr><td>").append(escape(firstNonBlank(quote.getStr("symbol", ""), quote.getStr("name", ""))))
                    .append("</td><td>").append(escape(value(quote, "price")))
                    .append("</td><td>").append(escape(value(quote, "change")))
                    .append("</td><td>").append(escape(value(quote, "changePercent")))
                    .append("</td><td>").append(escape(quote.getStr("date", "") + " " + quote.getStr("time", "")))
                    .append("</td></tr>");
        }
        html.append("</tbody></table></section>");
        html.append("<section><h2>来源引用</h2>");
        for (int i = 0; references != null && i < Math.min(MAX_REFERENCES, references.size()); i++) {
            JSONObject reference = references.getJSONObject(i);
            html.append("<p>").append(i + 1).append(". ").append(escape(firstNonBlank(reference.getStr("title", ""), reference.getStr("link", ""))))
                    .append("<br><span class=\"muted\">").append(escape(reference.getStr("link", ""))).append("</span></p>");
        }
        html.append("</section><section><h2>风险提示页</h2>");
        for (String line : splitLines(defaultIfBlank(riskTips, "市场有风险，投资需谨慎。本报告仅供信息参考，不构成任何投资建议。"))) {
            if (StrUtil.isNotBlank(line)) {
                html.append("<p>").append(escape(cleanMarkdownMarker(line))).append("</p>");
            }
        }
        html.append("</section></main></body></html>");
        return html.toString();
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
                JSONArray arr = JSONUtil.createArray();
                arr.add(object);
                return arr;
            }
        } catch (Exception ignored) {
        }
        return JSONUtil.createArray();
    }

    private String normalizePdfFileName(String fileName) {
        String fallback = "rich_report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
        String name = defaultIfBlank(fileName, fallback).trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (!name.toLowerCase().endsWith(".pdf")) {
            name += ".pdf";
        }
        return name;
    }

    private String[] splitLines(String text) {
        return defaultIfBlank(text, "").replace("\r\n", "\n").split("\n+");
    }

    private String cleanMarkdownMarker(String text) {
        return defaultIfBlank(text, "").replaceFirst("^\\s*[-*#]+\\s*", "").trim();
    }

    private String value(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : defaultIfBlank(second, "");
    }

    private String defaultIfBlank(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value;
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

    private JSONObject file(String label, String type, String path, boolean previewable) {
        JSONObject file = JSONUtil.createObj();
        file.set("label", label);
        file.set("type", type);
        file.set("path", path);
        file.set("previewable", previewable);
        return file;
    }
}
