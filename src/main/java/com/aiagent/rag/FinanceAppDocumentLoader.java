package com.aiagent.rag;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads Markdown and PDF resources into RAG-ready source documents with metadata.
 */
@Component
@Slf4j
public class FinanceAppDocumentLoader {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final int PDF_CHUNK_TARGET_CHARS = 2_800;

    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${app.rag.pdf.enabled:true}")
    private boolean pdfEnabled = true;

    @Value("${app.rag.pdf.max-documents:30}")
    private int maxPdfDocuments = 30;

    @Value("${app.rag.pdf.max-pages-per-document:80}")
    private int maxPdfPagesPerDocument = 80;

    @Value("${app.rag.pdf.max-chars-per-document:120000}")
    private int maxPdfCharsPerDocument = 120_000;

    public FinanceAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * Loads all RAG resources. Markdown files are loaded fully; PDF files are filtered
     * for finance relevance and split by page ranges before later semantic chunking.
     */
    public List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();
        documents.addAll(loadMarkdowns());
        if (pdfEnabled) {
            documents.addAll(loadPdfDocuments());
        } else {
            log.info("RAG PDF resource loading is disabled");
        }
        log.info("RAG source documents loaded: {}", documents.size());
        return documents;
    }

    /**
     * Loads Markdown documents by heading sections instead of one huge document per file.
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                allDocuments.addAll(loadMarkdownResource(resource));
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        log.info("RAG markdown source documents loaded: {}", allDocuments.size());
        return allDocuments;
    }

    private List<Document> loadMarkdownResource(Resource resource) {
        List<Document> documents = new ArrayList<>();
        String filename = safeFilename(resource);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String markdown = new String(bytes, StandardCharsets.UTF_8);
            String title = extractMarkdownTitle(markdown, filename);
            Map<String, Object> baseMetadata = baseMetadata(filename, title, "markdown");
            baseMetadata.put("sourcePath", "classpath:document/" + filename);
            baseMetadata.put("fileHash", sha256Hex(bytes));

            String currentHeading = title;
            StringBuilder section = new StringBuilder();
            int sectionIndex = 0;
            for (String line : markdown.split("\\R")) {
                Matcher matcher = MARKDOWN_HEADING.matcher(line);
                if (matcher.matches() && section.length() > 0) {
                    addMarkdownSection(documents, section.toString(), baseMetadata, currentHeading, sectionIndex++);
                    section.setLength(0);
                }
                if (matcher.matches()) {
                    currentHeading = matcher.group(2).trim();
                }
                section.append(line).append('\n');
            }
            if (section.length() > 0) {
                addMarkdownSection(documents, section.toString(), baseMetadata, currentHeading, sectionIndex);
            }
        } catch (Exception e) {
            log.warn("Failed to load markdown resource {}: {}", filename, e.getMessage());
        }
        return documents;
    }

    private void addMarkdownSection(List<Document> documents, String rawText, Map<String, Object> baseMetadata,
                                    String sectionTitle, int sectionIndex) {
        String normalized = normalizeText(rawText);
        if (normalized.length() < 60) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("sectionTitle", sectionTitle);
        metadata.put("sectionIndex", sectionIndex);
        metadata.put("sourceType", "knowledge_markdown");
        documents.add(new Document(enrichForEmbedding(metadata, normalized), metadata));
    }

    private List<Document> loadPdfDocuments() {
        List<Document> documents = new ArrayList<>();
        Map<String, PdfManifestEntry> manifest = loadPdfManifest();
        int loaded = 0;
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:finance_pdfs/*.pdf");
            for (Resource resource : resources) {
                String filename = safeFilename(resource);
                PdfManifestEntry entry = manifest.getOrDefault(filename, PdfManifestEntry.fromFilename(filename));
                if (!isFinanceRelevant(entry)) {
                    log.debug("Skip non-finance PDF for RAG: {}", filename);
                    continue;
                }
                if (loaded >= maxPdfDocuments) {
                    break;
                }
                List<Document> pdfDocs = loadPdfResource(resource, entry);
                if (!pdfDocs.isEmpty()) {
                    documents.addAll(pdfDocs);
                    loaded++;
                }
            }
        } catch (IOException e) {
            log.warn("PDF resources scan failed: {}", e.getMessage());
        }
        log.info("RAG PDF source documents loaded: {} chunks from {} files", documents.size(), loaded);
        return documents;
    }

    private List<Document> loadPdfResource(Resource resource, PdfManifestEntry entry) {
        List<Document> documents = new ArrayList<>();
        String filename = safeFilename(resource);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String fileHash = sha256Hex(bytes);
            try (PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
             PdfDocument pdfDocument = new PdfDocument(reader)) {
                int pages = Math.min(pdfDocument.getNumberOfPages(), Math.max(1, maxPdfPagesPerDocument));
                StringBuilder chunk = new StringBuilder();
                int chunkStartPage = 1;
                int chunkIndex = 0;
                int collectedChars = 0;
                for (int page = 1; page <= pages; page++) {
                    String pageText = normalizeText(PdfTextExtractor.getTextFromPage(pdfDocument.getPage(page)));
                    if (pageText.isBlank()) {
                        continue;
                    }
                    if (chunk.length() == 0) {
                        chunkStartPage = page;
                    }
                    chunk.append("第 ").append(page).append(" 页：").append(pageText).append("\n\n");
                    collectedChars += pageText.length();
                    if (chunk.length() >= PDF_CHUNK_TARGET_CHARS || collectedChars >= maxPdfCharsPerDocument) {
                        addPdfChunk(documents, entry, filename, fileHash, chunk.toString(), chunkStartPage, page, chunkIndex++);
                        chunk.setLength(0);
                    }
                    if (collectedChars >= maxPdfCharsPerDocument) {
                        break;
                    }
                }
                if (chunk.length() > 0) {
                    addPdfChunk(documents, entry, filename, fileHash, chunk.toString(), chunkStartPage, pages, chunkIndex);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract PDF {} for RAG: {}", filename, e.getMessage());
        }
        return documents;
    }

    private void addPdfChunk(List<Document> documents, PdfManifestEntry entry, String filename, String fileHash,
                             String rawText, int pageStart, int pageEnd, int chunkIndex) {
        String normalized = normalizeText(rawText);
        if (normalized.length() < 120) {
            return;
        }
        Map<String, Object> metadata = baseMetadata(filename, entry.title(), "pdf");
        metadata.put("sourcePath", "classpath:finance_pdfs/" + filename);
        metadata.put("fileHash", fileHash);
        metadata.put("sourceType", "finance_pdf");
        metadata.put("bookId", entry.bookId());
        metadata.put("authors", entry.authors());
        metadata.put("subjects", entry.subjects());
        metadata.put("queryGroup", entry.queryGroup());
        metadata.put("query", entry.query());
        metadata.put("pageStart", pageStart);
        metadata.put("pageEnd", pageEnd);
        metadata.put("sectionTitle", "PDF 第 " + pageStart + "-" + pageEnd + " 页");
        metadata.put("sectionIndex", chunkIndex);
        documents.add(new Document(enrichForEmbedding(metadata, normalized), metadata));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private Map<String, PdfManifestEntry> loadPdfManifest() {
        Map<String, PdfManifestEntry> entries = new LinkedHashMap<>();
        try {
            Resource resource = resourcePatternResolver.getResource("classpath:finance_pdfs/manifest.json");
            if (!resource.exists()) {
                return entries;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                JSONArray array = JSONUtil.parseArray(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                for (Object item : array) {
                    if (!(item instanceof JSONObject object)) {
                        continue;
                    }
                    PdfManifestEntry entry = PdfManifestEntry.fromJson(object);
                    if (entry.filename() != null && !entry.filename().isBlank()) {
                        entries.put(entry.filename(), entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load PDF manifest, fallback to filename metadata: {}", e.getMessage());
        }
        return entries;
    }

    private boolean isFinanceRelevant(PdfManifestEntry entry) {
        String text = (entry.queryGroup() + " " + entry.query() + " " + entry.title() + " " + entry.subjects())
                .toLowerCase(Locale.ROOT);
        String[] negativeTerms = {
                "vagabond", "botany", "pneumonia", "polio", "recipes", "cooking", "lead poisoning",
                "blockade", "christian mystery", "cedar", "immigration", "reconstruction of georgia",
                "tacoma", "gallant", "paralysis", "overweight", "underweight"
        };
        for (String term : negativeTerms) {
            if (text.contains(term)) {
                return false;
            }
        }
        String[] positiveTerms = {
                "stock exchange", "stocks", "speculation", "investment", "investments", "money",
                "bank", "banking", "economics", "political economy", "tax", "stamp duties",
                "insurance", "health insurance", "gold standard", "currency", "business economics",
                "financial crises", "sugar futures", "commerce", "commercial law"
        };
        for (String term : positiveTerms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> baseMetadata(String filename, String title, String documentType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", filename);
        metadata.put("title", title);
        metadata.put("documentType", documentType);
        metadata.put("category", classifyCategory(filename + " " + title));
        metadata.put("assetClass", classifyAssetClass(filename + " " + title));
        metadata.put("status", classifyCategory(filename + " " + title));
        metadata.put("language", containsChinese(title) || containsChinese(filename) ? "zh" : "en");
        return metadata;
    }

    private String enrichForEmbedding(Map<String, Object> metadata, String text) {
        return """
                标题: %s
                分类: %s
                资产类别: %s
                资料类型: %s
                章节: %s

                %s
                """.formatted(
                value(metadata, "title"),
                value(metadata, "category"),
                value(metadata, "assetClass"),
                value(metadata, "documentType"),
                value(metadata, "sectionTitle"),
                text
        ).trim();
    }

    private String classifyCategory(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "股票", "stock", "exchange", "speculation", "ipo")) return "stocks";
        if (containsAny(lower, "基金", "fund", "etf")) return "funds";
        if (containsAny(lower, "债券", "bond", "fixed income", "credit")) return "bonds";
        if (containsAny(lower, "黄金", "gold", "commodity", "sugar futures")) return "gold_commodities";
        if (containsAny(lower, "保险", "insurance", "annuity")) return "insurance";
        if (containsAny(lower, "税", "tax", "stamp duties")) return "tax";
        if (containsAny(lower, "风险", "risk", "var", "drawdown")) return "risk_management";
        if (containsAny(lower, "监管", "合规", "regulation", "compliance", "law")) return "regulation";
        if (containsAny(lower, "资产配置", "portfolio", "allocation")) return "asset_allocation";
        if (containsAny(lower, "宏观", "economics", "political economy", "money", "banking", "currency")) return "macro";
        return "general_finance";
    }

    private String classifyAssetClass(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "股票", "stock", "equity", "exchange")) return "equity";
        if (containsAny(lower, "基金", "fund", "etf")) return "fund";
        if (containsAny(lower, "债券", "bond", "fixed income", "credit")) return "fixed_income";
        if (containsAny(lower, "黄金", "gold", "commodity", "sugar futures")) return "commodity";
        if (containsAny(lower, "保险", "insurance", "annuity")) return "insurance";
        if (containsAny(lower, "税", "tax")) return "tax";
        if (containsAny(lower, "宏观", "economics", "banking", "currency", "money")) return "macro";
        return "multi_asset";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractMarkdownTitle(String markdown, String filename) {
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return filename.replaceFirst("\\.md$", "");
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String safeFilename(Resource resource) {
        String filename = resource.getFilename();
        return filename == null ? "unknown" : filename;
    }

    private String value(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private record PdfManifestEntry(
            String filename,
            String queryGroup,
            String query,
            String bookId,
            String title,
            String authors,
            String subjects
    ) {
        static PdfManifestEntry fromJson(JSONObject object) {
            String file = object.getStr("file", "");
            String filename = file.substring(file.lastIndexOf('/') + 1);
            return new PdfManifestEntry(
                    filename,
                    object.getStr("query_group", ""),
                    object.getStr("query", ""),
                    object.getStr("book_id", ""),
                    object.getStr("title", filename.replaceFirst("\\.pdf$", "")),
                    object.getStr("authors", ""),
                    object.getStr("subjects", "")
            );
        }

        static PdfManifestEntry fromFilename(String filename) {
            return new PdfManifestEntry(
                    filename,
                    "",
                    "",
                    "",
                    filename.replaceFirst("^\\d+-", "").replaceFirst("\\.pdf$", ""),
                    "",
                    ""
            );
        }
    }
}
