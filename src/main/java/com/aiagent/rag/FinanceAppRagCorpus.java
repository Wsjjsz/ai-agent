package com.aiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a lightweight in-memory keyword index beside the vector store.
 *
 * <p>Vector search is good at semantic similarity; this corpus improves exact
 * matches for finance terms such as 久期、股息红利税、场外配资、VaR.</p>
 */
@Component
public class FinanceAppRagCorpus {

    private volatile List<Document> documents = List.of();

    public void replaceAll(List<Document> documents) {
        this.documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public List<Document> keywordSearch(String query, int limit) {
        if (query == null || query.isBlank() || documents.isEmpty()) {
            return List.of();
        }
        List<ScoredDocument> scored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            double score = score(query, document);
            if (score > 0) {
                scored.add(new ScoredDocument(document, score, i));
            }
        }
        return scored.stream()
                .sorted((left, right) -> {
                    int scoreCompare = Double.compare(right.score(), left.score());
                    return scoreCompare != 0 ? scoreCompare : Integer.compare(left.index(), right.index());
                })
                .map(ScoredDocument::document)
                .limit(Math.max(1, limit))
                .toList();
    }

    public int size() {
        return documents.size();
    }

    private double score(String query, Document document) {
        String q = normalize(query);
        String haystack = normalize(document.getText() + " " + metadataText(document.getMetadata()));
        double score = 0;

        Set<String> matchedKeywords = new HashSet<>();
        for (String keyword : FinanceRagTerms.KEYWORDS) {
            String k = normalize(keyword);
            if (q.contains(k) && haystack.contains(k) && matchedKeywords.add(k)) {
                score += k.length() >= 3 ? 3.0 : 1.6;
            }
        }

        for (String token : q.split("[^a-z0-9]+")) {
            if (token.length() >= 3 && haystack.contains(token)) {
                score += 1.0;
            }
        }

        Object category = document.getMetadata() == null ? null : document.getMetadata().get("category");
        if (category != null && q.contains(normalize(category.toString()))) {
            score += 1.2;
        }
        score += FinanceRagQueryPlanner.metadataBoost(query, document);
        return score;
    }

    private String metadataText(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        metadata.forEach((key, value) -> sb.append(key).append(' ').append(value).append(' '));
        return sb.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record ScoredDocument(Document document, double score, int index) {
    }
}
