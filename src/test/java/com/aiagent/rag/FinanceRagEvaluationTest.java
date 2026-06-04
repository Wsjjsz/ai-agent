package com.aiagent.rag;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceRagEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(FinanceRagEvaluationTest.class);
    private static final Path REPORT_DIR = Path.of("target", "rag-eval");
    private static final Path MARKDOWN_REPORT = REPORT_DIR.resolve("rag-eval-report.md");
    private static final Path JSON_REPORT = REPORT_DIR.resolve("rag-eval-report.json");

    @Test
    void keywordCorpusShouldHitExpectedCategoryInTopFive() throws Exception {
        FinanceAppDocumentLoader loader =
                new FinanceAppDocumentLoader(new PathMatchingResourcePatternResolver());
        MyTokenTextSplitter splitter = new MyTokenTextSplitter();
        FinanceAppRagCorpus corpus = new FinanceAppRagCorpus();
        corpus.replaceAll(splitter.splitForRag(loader.loadMarkdowns()));

        List<EvalCase> cases = loadEvalCases();
        EvalResult result = evaluate(cases, corpus);
        writeReports(result, corpus.size());

        log.info("RAG eval result: category hit@3={}/{} ({}), category hit@5={}/{} ({}), keyword hit@5={}/{} ({}), category MRR={}",
                result.categoryHitAt3(), result.total(), result.hitAt3(),
                result.categoryHitAt5(), result.total(), result.hitAt5(),
                result.keywordHitAt5(), result.total(), result.keywordAt5(),
                result.mrr());
        log.info("RAG eval reports generated: {}, {}", MARKDOWN_REPORT.toAbsolutePath(), JSON_REPORT.toAbsolutePath());

        assertTrue(result.hitAt3() >= 0.7, "RAG eval category hit@3 too low: " + result.hitAt3());
        assertTrue(result.hitAt5() >= 0.8, "RAG eval category hit@5 too low: " + result.hitAt5());
        assertTrue(result.keywordAt5() >= 0.8, "RAG eval keyword hit@5 too low: " + result.keywordAt5());
        assertTrue(result.mrr() >= 0.65, "RAG eval category MRR too low: " + result.mrr());
    }

    private List<EvalCase> loadEvalCases() throws Exception {
        JSONArray items = JSONUtil.parseArray(new String(
                new ClassPathResource("rag/rag-eval-cases.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ));
        List<EvalCase> cases = new ArrayList<>();
        for (Object item : items) {
            JSONObject object = (JSONObject) item;
            cases.add(new EvalCase(
                    object.getStr("query"),
                    object.getStr("expectedCategory"),
                    object.getJSONArray("expectedAnyKeyword").toList(String.class)
            ));
        }
        return cases;
    }

    private EvalResult evaluate(List<EvalCase> cases, FinanceAppRagCorpus corpus) {
        List<CaseResult> caseResults = new ArrayList<>();
        Map<String, CategoryStats> byCategory = new LinkedHashMap<>();
        int categoryHitAt3 = 0;
        int categoryHitAt5 = 0;
        int keywordHitAt5 = 0;
        double categoryMrr = 0;

        for (EvalCase evalCase : cases) {
            List<Document> topFive = corpus.keywordSearch(evalCase.query(), 5);
            int categoryRank = firstRank(topFive, document -> evalCase.expectedCategory().equals(categoryOf(document)));
            int keywordRank = firstRank(topFive, document -> containsAny(document, evalCase.expectedKeywords()));

            CategoryStats stats = byCategory.computeIfAbsent(evalCase.expectedCategory(), CategoryStats::new);
            stats.total++;
            if (categoryRank > 0 && categoryRank <= 3) {
                categoryHitAt3++;
                stats.categoryHitAt3++;
            }
            if (categoryRank > 0 && categoryRank <= 5) {
                categoryHitAt5++;
                stats.categoryHitAt5++;
                categoryMrr += 1.0 / categoryRank;
                stats.mrr += 1.0 / categoryRank;
            }
            if (keywordRank > 0 && keywordRank <= 5) {
                keywordHitAt5++;
                stats.keywordHitAt5++;
            }

            CaseResult result = new CaseResult(
                    evalCase.query(),
                    evalCase.expectedCategory(),
                    evalCase.expectedKeywords(),
                    categoryRank,
                    keywordRank,
                    topFive.stream().map(this::categoryOf).toList(),
                    topFive.stream().map(this::sourceOf).toList()
            );
            caseResults.add(result);
            log.info("RAG eval case: query='{}', expectedCategory={}, categoryRank={}, keywordRank={}",
                    result.query(), result.expectedCategory(), result.categoryRank(), result.keywordRank());
        }

        int total = cases.size();
        return new EvalResult(
                total,
                categoryHitAt3,
                categoryHitAt5,
                keywordHitAt5,
                categoryMrr,
                caseResults,
                byCategory
        );
    }

    private void writeReports(EvalResult result, int corpusSize) throws Exception {
        Files.createDirectories(REPORT_DIR);
        Files.writeString(MARKDOWN_REPORT, toMarkdownReport(result, corpusSize), StandardCharsets.UTF_8);
        Files.writeString(JSON_REPORT, JSONUtil.toJsonPrettyStr(toJsonReport(result, corpusSize)), StandardCharsets.UTF_8);
    }

    private String toMarkdownReport(EvalResult result, int corpusSize) {
        StringBuilder report = new StringBuilder();
        report.append("# Finance RAG Evaluation Report\n\n");
        report.append("## Summary\n\n");
        report.append("- Eval cases: ").append(result.total()).append('\n');
        report.append("- Corpus chunks: ").append(corpusSize).append('\n');
        report.append("- Category hit@3: ").append(result.categoryHitAt3()).append('/').append(result.total())
                .append(" (").append(rate(result.hitAt3())).append(")\n");
        report.append("- Category hit@5: ").append(result.categoryHitAt5()).append('/').append(result.total())
                .append(" (").append(rate(result.hitAt5())).append(")\n");
        report.append("- Keyword hit@5: ").append(result.keywordHitAt5()).append('/').append(result.total())
                .append(" (").append(rate(result.keywordAt5())).append(")\n");
        report.append("- Category MRR: ").append(format(result.mrr())).append("\n\n");

        report.append("## Thresholds\n\n");
        report.append("| Metric | Threshold | Current |\n");
        report.append("|---|---:|---:|\n");
        report.append("| category hit@3 | 70.00% | ").append(rate(result.hitAt3())).append(" |\n");
        report.append("| category hit@5 | 80.00% | ").append(rate(result.hitAt5())).append(" |\n");
        report.append("| keyword hit@5 | 80.00% | ").append(rate(result.keywordAt5())).append(" |\n");
        report.append("| category MRR | 0.6500 | ").append(format(result.mrr())).append(" |\n\n");

        report.append("## Metrics By Category\n\n");
        report.append("| Category | Cases | hit@3 | hit@5 | keyword@5 | MRR |\n");
        report.append("|---|---:|---:|---:|---:|---:|\n");
        for (CategoryStats stats : result.byCategory().values()) {
            report.append("| ").append(stats.category).append(" | ")
                    .append(stats.total).append(" | ")
                    .append(rate(stats.hitAt3())).append(" | ")
                    .append(rate(stats.hitAt5())).append(" | ")
                    .append(rate(stats.keywordAt5())).append(" | ")
                    .append(format(stats.mrr())).append(" |\n");
        }

        List<CaseResult> weakCases = weakCases(result.caseResults());
        report.append("\n## Weak Cases\n\n");
        if (weakCases.isEmpty()) {
            report.append("No weak cases. All expected categories and keywords were hit at rank 1.\n");
        } else {
            report.append("| Query | Expected | Category rank | Keyword rank | Top categories |\n");
            report.append("|---|---|---:|---:|---|\n");
            for (CaseResult item : weakCases) {
                report.append("| ").append(escape(item.query())).append(" | ")
                        .append(item.expectedCategory()).append(" | ")
                        .append(item.categoryRank()).append(" | ")
                        .append(item.keywordRank()).append(" | ")
                        .append(escape(String.join(", ", item.topCategories()))).append(" |\n");
            }
        }
        return report.toString();
    }

    private JSONObject toJsonReport(EvalResult result, int corpusSize) {
        JSONObject report = new JSONObject();
        report.set("total", result.total());
        report.set("corpusChunks", corpusSize);
        report.set("metrics", metricsJson(
                result.categoryHitAt3(),
                result.categoryHitAt5(),
                result.keywordHitAt5(),
                result.total(),
                result.mrr()
        ));
        report.set("thresholds", thresholdsJson());

        JSONArray categories = new JSONArray();
        for (CategoryStats stats : result.byCategory().values()) {
            JSONObject item = new JSONObject();
            item.set("category", stats.category);
            item.set("total", stats.total);
            item.set("categoryHitAt3", stats.hitAt3());
            item.set("categoryHitAt5", stats.hitAt5());
            item.set("keywordHitAt5", stats.keywordAt5());
            item.set("mrr", stats.mrr());
            categories.add(item);
        }
        report.set("byCategory", categories);

        JSONArray weak = new JSONArray();
        for (CaseResult item : weakCases(result.caseResults())) {
            JSONObject object = new JSONObject();
            object.set("query", item.query());
            object.set("expectedCategory", item.expectedCategory());
            object.set("expectedKeywords", item.expectedKeywords());
            object.set("categoryRank", item.categoryRank());
            object.set("keywordRank", item.keywordRank());
            object.set("topCategories", item.topCategories());
            object.set("topSources", item.topSources());
            weak.add(object);
        }
        report.set("weakCases", weak);
        return report;
    }

    private JSONObject metricsJson(int categoryHitAt3, int categoryHitAt5, int keywordHitAt5, int total, double mrr) {
        JSONObject metrics = new JSONObject();
        metrics.set("categoryHitAt3", ratioJson(categoryHitAt3, total));
        metrics.set("categoryHitAt5", ratioJson(categoryHitAt5, total));
        metrics.set("keywordHitAt5", ratioJson(keywordHitAt5, total));
        metrics.set("categoryMrr", mrr);
        return metrics;
    }

    private JSONObject thresholdsJson() {
        JSONObject thresholds = new JSONObject();
        thresholds.set("categoryHitAt3", 0.7);
        thresholds.set("categoryHitAt5", 0.8);
        thresholds.set("keywordHitAt5", 0.8);
        thresholds.set("categoryMrr", 0.65);
        return thresholds;
    }

    private JSONObject ratioJson(int count, int total) {
        JSONObject value = new JSONObject();
        value.set("count", count);
        value.set("total", total);
        value.set("rate", total == 0 ? 0 : count * 1.0 / total);
        return value;
    }

    private List<CaseResult> weakCases(List<CaseResult> caseResults) {
        return caseResults.stream()
                .filter(item -> item.categoryRank() != 1 || item.keywordRank() != 1)
                .toList();
    }

    private int firstRank(List<Document> documents, Predicate<Document> predicate) {
        for (int i = 0; i < documents.size(); i++) {
            if (predicate.test(documents.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private String categoryOf(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return metadata == null ? "" : String.valueOf(metadata.getOrDefault("category", ""));
    }

    private String sourceOf(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            return "";
        }
        Object filename = metadata.getOrDefault("filename", "");
        Object chunkId = metadata.getOrDefault("chunkId", "");
        return filename + " " + chunkId;
    }

    private boolean containsAny(Document document, List<String> keywords) {
        StringBuilder text = new StringBuilder(document.getText() == null ? "" : document.getText());
        if (document.getMetadata() != null) {
            document.getMetadata().forEach((key, value) -> text.append(' ').append(key).append(' ').append(value));
        }
        return keywords.stream().anyMatch(keyword -> text.toString().contains(keyword));
    }

    private String rate(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private record EvalCase(String query, String expectedCategory, List<String> expectedKeywords) {
    }

    private record CaseResult(
            String query,
            String expectedCategory,
            List<String> expectedKeywords,
            int categoryRank,
            int keywordRank,
            List<String> topCategories,
            List<String> topSources
    ) {
    }

    private record EvalResult(
            int total,
            int categoryHitAt3,
            int categoryHitAt5,
            int keywordHitAt5,
            double categoryMrr,
            List<CaseResult> caseResults,
            Map<String, CategoryStats> byCategory
    ) {
        double hitAt3() {
            return total == 0 ? 0 : categoryHitAt3 * 1.0 / total;
        }

        double hitAt5() {
            return total == 0 ? 0 : categoryHitAt5 * 1.0 / total;
        }

        double keywordAt5() {
            return total == 0 ? 0 : keywordHitAt5 * 1.0 / total;
        }

        double mrr() {
            return total == 0 ? 0 : categoryMrr / total;
        }
    }

    private static final class CategoryStats {
        private final String category;
        private int total;
        private int categoryHitAt3;
        private int categoryHitAt5;
        private int keywordHitAt5;
        private double mrr;

        private CategoryStats(String category) {
            this.category = category;
        }

        private double hitAt3() {
            return total == 0 ? 0 : categoryHitAt3 * 1.0 / total;
        }

        private double hitAt5() {
            return total == 0 ? 0 : categoryHitAt5 * 1.0 / total;
        }

        private double keywordAt5() {
            return total == 0 ? 0 : keywordHitAt5 * 1.0 / total;
        }

        private double mrr() {
            return total == 0 ? 0 : mrr / total;
        }
    }
}
