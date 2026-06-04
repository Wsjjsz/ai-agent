package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.aiagent.files.GeneratedFileContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PDF 生成工具 — 支持 Markdown 结构化内容
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        Path target = GeneratedFileContext.resolve("pdf", fileName);
        String filePath = target.toString();
        try {
            FileUtil.mkdir(target.getParent().toString());
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);

                String[] lines = content.split("\n");
                for (String line : lines) {
                    Paragraph p;
                    if (line.startsWith("# ")) {
                        p = new Paragraph(line.substring(2)).setFontSize(18f);
                        p.setTextAlignment(TextAlignment.LEFT);
                    } else if (line.startsWith("## ")) {
                        p = new Paragraph(line.substring(3)).setFontSize(15f);
                    } else if (line.startsWith("### ")) {
                        p = new Paragraph(line.substring(4)).setFontSize(13f);
                    } else if (line.startsWith("- ") || line.startsWith("* ")) {
                        p = new Paragraph("  • " + line.substring(2)).setFontSize(11f);
                    } else if (line.matches("^\\d+\\.\\s.*")) {
                        p = new Paragraph("  " + line).setFontSize(11f);
                    } else if (line.trim().isEmpty()) {
                        p = new Paragraph("\n").setFontSize(6f);
                    } else {
                        p = new Paragraph(line).setFontSize(11f);
                    }
                    document.add(p);
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }
}
