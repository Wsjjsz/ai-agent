package com.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceRagQueryPlannerTest {

    @Test
    void detectCategoriesForPreciseFinanceQueries() {
        assertTrue(FinanceRagQueryPlanner.detectCategories("债券基金久期风险怎么看").contains("bonds"));
        assertTrue(FinanceRagQueryPlanner.detectCategories("股息红利税怎么算").contains("tax"));
        assertTrue(FinanceRagQueryPlanner.detectCategories("场外配资有什么合规风险").contains("regulation"));
        assertTrue(FinanceRagQueryPlanner.detectCategories("黄金和实际利率是什么关系").contains("gold_commodities"));
    }

    @Test
    void metadataBoostRewardsMatchedCategory() {
        Document document = new Document("债券基金需要关注久期。", Map.of(
                "category", "bonds",
                "assetClass", "fixed_income"
        ));

        assertTrue(FinanceRagQueryPlanner.metadataBoost("久期风险", document) > 0);
    }

    @Test
    void expandQueriesAddsDomainTerms() {
        Set<String> queries = FinanceRagQueryPlanner.expandQueries("黄金还能买吗", 3);

        assertTrue(queries.stream().anyMatch(query -> query.contains("实际利率")));
    }
}
