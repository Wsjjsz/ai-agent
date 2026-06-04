package com.aiagent.rag;

import org.springframework.ai.document.Document;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Domain-aware query planner for finance RAG.
 */
public final class FinanceRagQueryPlanner {

    private FinanceRagQueryPlanner() {
    }

    public static Set<String> detectCategories(String query) {
        String q = normalize(query);
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        addIfMatches(categories, "stocks", q, "股票", "普通股", "优先股", "估值", "市盈率", "市净率", "股息", "分红", "ipo", "stock", "equity");
        addIfMatches(categories, "funds", q, "基金", "etf", "fof", "qdii", "定投", "净值", "申购", "赎回", "基金费用", "销售服务费", "fund");
        addIfMatches(categories, "bonds", q, "债券", "久期", "票息", "信用利差", "到期收益率", "利率风险", "可转债", "bond", "duration", "fixed income");
        addIfMatches(categories, "gold_commodities", q, "黄金", "大宗", "商品", "原油", "铜", "实际利率", "央行购金", "gold", "commodity");
        addIfMatches(categories, "insurance", q, "保险", "寿险", "重疾", "医疗险", "意外险", "年金", "保额", "insurance", "annuity");
        addIfMatches(categories, "macro", q, "宏观", "gdp", "cpi", "ppi", "pmi", "通胀", "货币政策", "社融", "汇率", "美联储", "降息", "加息", "inflation", "interest rate");
        addIfMatches(categories, "tax", q, "税", "税务", "个税", "股息红利税", "资本利得", "预扣税", "tax");
        addIfMatches(categories, "asset_allocation", q, "资产配置", "组合", "再平衡", "分散化", "应急备用金", "portfolio", "allocation");
        addIfMatches(categories, "risk_management", q, "风险", "回撤", "var", "压力测试", "夏普", "流动性风险", "risk", "drawdown");
        addIfMatches(categories, "regulation", q, "监管", "合规", "适当性", "非法集资", "非法荐股", "场外配资", "内幕交易", "保本高收益", "刚性兑付", "regulation", "compliance");
        return categories;
    }

    public static Set<String> expandQueries(String query, int limit) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String normalized = query == null ? "" : query.trim();
        if (!normalized.isBlank()) {
            queries.add(normalized);
        }
        for (String category : detectCategories(normalized)) {
            queries.add(normalized + " " + expansionFor(category));
            if (queries.size() >= limit) {
                break;
            }
        }
        return queries;
    }

    public static double metadataBoost(String query, Document document) {
        if (document == null || document.getMetadata() == null) {
            return 0;
        }
        Set<String> categories = detectCategories(query);
        if (categories.isEmpty()) {
            return 0;
        }
        Map<String, Object> metadata = document.getMetadata();
        String category = normalize(String.valueOf(metadata.getOrDefault("category", "")));
        String assetClass = normalize(String.valueOf(metadata.getOrDefault("assetClass", "")));
        double boost = 0;
        for (String expected : categories) {
            if (category.equals(expected)) {
                boost += 3.0;
                boost += strongCategoryBoost(query, expected);
            }
            if (assetClassMatches(expected, assetClass)) {
                boost += 1.4;
            }
        }
        return boost;
    }

    private static double strongCategoryBoost(String query, String category) {
        String q = normalize(query);
        return switch (category) {
            case "bonds" -> containsAny(q, "久期", "信用利差", "到期收益率", "票息", "可转债") ? 3.0 : 0;
            case "tax" -> containsAny(q, "股息红利税", "资本利得", "预扣税") ? 2.5 : 0;
            case "regulation" -> containsAny(q, "场外配资", "非法集资", "非法荐股", "内幕交易") ? 2.5 : 0;
            case "gold_commodities" -> containsAny(q, "实际利率", "央行购金", "美元指数") ? 2.5 : 0;
            case "insurance" -> containsAny(q, "重疾险", "医疗险", "定期寿险", "责任免除") ? 2.0 : 0;
            default -> 0;
        };
    }

    private static void addIfMatches(Set<String> categories, String category, String query, String... terms) {
        for (String term : terms) {
            if (query.contains(normalize(term))) {
                categories.add(category);
                return;
            }
        }
    }

    private static String expansionFor(String category) {
        return switch (category) {
            case "stocks" -> "股票 估值 市盈率 基本面 财务质量 信息披露";
            case "funds" -> "基金 ETF 指数基金 主动基金 费用 跟踪误差 风险";
            case "bonds" -> "债券 久期 到期收益率 利率风险 信用风险 固定收益";
            case "gold_commodities" -> "黄金 实际利率 美元 通胀 央行购金 大宗商品";
            case "insurance" -> "保险 医疗险 重疾险 定期寿险 保额 责任免除 续保";
            case "macro" -> "宏观 GDP CPI PMI 通胀 货币政策 利率 社融 汇率";
            case "tax" -> "税务 个人所得税 股息红利税 资本利得 成本基础 预扣税";
            case "asset_allocation" -> "资产配置 战略资产配置 再平衡 风险承受能力 分散化";
            case "risk_management" -> "风险管理 最大回撤 VaR 压力测试 流动性风险 集中度风险";
            case "regulation" -> "监管 合规 投资者保护 适当性 非法集资 场外配资 风险揭示";
            default -> "";
        };
    }

    private static boolean assetClassMatches(String category, String assetClass) {
        return switch (category) {
            case "stocks" -> "equity".equals(assetClass);
            case "funds" -> "fund".equals(assetClass);
            case "bonds" -> "fixed_income".equals(assetClass);
            case "gold_commodities" -> "commodity".equals(assetClass);
            case "insurance" -> "insurance".equals(assetClass);
            case "macro" -> "macro".equals(assetClass);
            case "tax" -> "tax".equals(assetClass);
            default -> false;
        };
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
