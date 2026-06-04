package com.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FinanceAppRagCorpusTest {

    @Test
    void keywordSearchPrefersExactFinanceTerms() {
        FinanceAppRagCorpus corpus = new FinanceAppRagCorpus();
        Document duration = new Document(
                "债券基金需要关注久期、到期收益率、利率风险和信用风险。",
                Map.of("category", "bonds", "filename", "bond.md", "chunkId", "bond#0")
        );
        Document gold = new Document(
                "黄金配置主要关注实际利率、美元指数和央行购金。",
                Map.of("category", "gold_commodities", "filename", "gold.md", "chunkId", "gold#0")
        );
        corpus.replaceAll(List.of(gold, duration));

        List<Document> results = corpus.keywordSearch("债券基金久期风险怎么看", 2);

        assertFalse(results.isEmpty());
        assertEquals("bond.md", results.get(0).getMetadata().get("filename"));
    }

    @Test
    void keywordSearchUsesMetadataCategoryBoost() {
        FinanceAppRagCorpus corpus = new FinanceAppRagCorpus();
        Document regulation = new Document(
                "不得通过无资质渠道提供证券投资建议或引导高杠杆交易。",
                Map.of("category", "regulation", "filename", "compliance.md", "chunkId", "compliance#0")
        );
        Document stock = new Document(
                "股票估值可以观察市盈率和市净率。",
                Map.of("category", "stocks", "filename", "stock.md", "chunkId", "stock#0")
        );
        corpus.replaceAll(List.of(stock, regulation));

        List<Document> results = corpus.keywordSearch("场外配资有什么合规风险", 2);

        assertFalse(results.isEmpty());
        assertEquals("compliance.md", results.get(0).getMetadata().get("filename"));
    }
}
