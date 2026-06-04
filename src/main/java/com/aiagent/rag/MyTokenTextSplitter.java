package com.aiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG-oriented semantic splitter that preserves source metadata.
 */
@Component
class MyTokenTextSplitter {

    static final String VERSION = "rag-token-splitter-v1";

    private static final int DEFAULT_CHUNK_CHARS = 1_200;
    private static final int DEFAULT_OVERLAP_CHARS = 160;
    private static final int MIN_CHUNK_CHARS = 180;

    public List<Document> splitDocuments(List<Document> documents) {
        return splitForRag(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        return splitForRag(documents);
    }

    public List<Document> splitForRag(List<Document> documents) {
        List<Document> chunks = new ArrayList<>();
        if (documents == null || documents.isEmpty()) {
            return chunks;
        }
        for (Document document : documents) {
            chunks.addAll(splitOne(document));
        }
        return chunks;
    }

    private List<Document> splitOne(Document document) {
        List<Document> chunks = new ArrayList<>();
        if (document == null || document.getText() == null || document.getText().isBlank()) {
            return chunks;
        }
        String text = normalize(document.getText());
        if (text.length() <= DEFAULT_CHUNK_CHARS) {
            chunks.add(copyWithChunkMetadata(document, text, 0, 0, text.length(), false));
            return chunks;
        }

        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + DEFAULT_CHUNK_CHARS);
            int end = findBoundary(text, start, hardEnd);
            if (end <= start) {
                end = hardEnd;
            }

            String chunkText = text.substring(start, end).trim();
            if (chunkText.length() >= MIN_CHUNK_CHARS) {
                chunks.add(copyWithChunkMetadata(document, chunkText, index++, start, end, true));
            }

            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - DEFAULT_OVERLAP_CHARS);
            if (text.length() - start < MIN_CHUNK_CHARS) {
                break;
            }
        }
        return chunks;
    }

    private Document copyWithChunkMetadata(Document source, String text, int chunkIndex,
                                           int start, int end, boolean split) {
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        metadata.put("splitterVersion", VERSION);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkStart", start);
        metadata.put("chunkEnd", end);
        metadata.put("chunkChars", text.length());
        metadata.put("split", split);
        String chunkIdBase = String.valueOf(metadata.getOrDefault("filename", "document"));
        metadata.put("chunkId", chunkIdBase + "#" + chunkIndex);
        return new Document(text, metadata);
    }

    private int findBoundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        int min = start + Math.max(MIN_CHUNK_CHARS, DEFAULT_CHUNK_CHARS / 2);
        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= min) {
            return paragraph;
        }
        int newline = text.lastIndexOf('\n', hardEnd);
        if (newline >= min) {
            return newline;
        }
        int chinesePeriod = text.lastIndexOf('。', hardEnd);
        if (chinesePeriod >= min) {
            return chinesePeriod + 1;
        }
        int semicolon = text.lastIndexOf('；', hardEnd);
        if (semicolon >= min) {
            return semicolon + 1;
        }
        int period = text.lastIndexOf(". ", hardEnd);
        if (period >= min) {
            return period + 1;
        }
        int comma = text.lastIndexOf('，', hardEnd);
        if (comma >= min) {
            return comma + 1;
        }
        return hardEnd;
    }

    private String normalize(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
