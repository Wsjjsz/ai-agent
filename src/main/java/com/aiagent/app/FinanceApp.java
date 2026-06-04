package com.aiagent.app;

import com.aiagent.advisor.MyLoggerAdvisor;
import com.aiagent.advisor.ReReadingAdvisor;
import com.aiagent.chatmemory.FileBasedChatMemory;
import com.aiagent.rag.FinanceAppRagCorpus;
import com.aiagent.rag.FinanceRagQueryPlanner;
import com.aiagent.rag.FinanceRagTerms;
import com.aiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FinanceApp implements FinanceAppService {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是一位专业的金融理财顾问，擅长投资、理财、股票及基金领域。" +
            "请遵守以下规则：\n" +
            "1. 仅在对话的第一条消息中简短介绍自己，后续消息直接回答用户问题，不要重复自我介绍。\n" +
            "2. 根据用户的具体问题给出专业、简洁、有针对性的回答。\n" +
            "3. 如果需要了解用户情况以给出更好建议，可以简短提问，但不要一次性列出大量问题。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public FinanceApp(ChatModel dashscopeChatModel, com.aiagent.history.JdbcChatMemory jdbcChatMemory) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        org.springframework.ai.chat.memory.ChatMemory chatMemory = jdbcChatMemory;
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId, long userId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId, long userId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                .stream()
                .content()
                .onErrorResume(e -> Flux.just(buildQuotaErrorMessage(e)));
    }

    private String buildQuotaErrorMessage(Throwable e) {
        StringBuilder errInfo = new StringBuilder();
        if (e.getMessage() != null) errInfo.append(e.getMessage()).append(" ");
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause.getMessage() != null) errInfo.append(cause.getMessage()).append(" ");
            cause = cause.getCause();
        }
        if (e instanceof RestClientResponseException rre) {
            try {
                String body = rre.getResponseBodyAsString();
                if (body != null) errInfo.append(body).append(" ");
            } catch (Exception ignored) {}
        }
        String errMsg = errInfo.toString();
        if (errMsg.contains("AllocationQuota") || errMsg.contains("FreeTierOnly")
                || errMsg.contains("free tier") || errMsg.contains("exhausted")
                || errMsg.contains("quota") || errMsg.contains("额度")) {
            return "额度已用完，请等待充值～";
        }
        return "处理时遇到了错误：" + (e.getMessage() != null ? e.getMessage() : "未知错误");
    }

    public record FinanceReport(String title, List<String> suggestions) {

    }

    /**
     * AI 理财报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public FinanceReport doChatWithReport(String message, String chatId, long userId) {
        FinanceReport financeReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成理财结果，标题为{用户名}的理财报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                .call()
                .entity(FinanceReport.class);
        log.info("financeReport: {}", financeReport);
        return financeReport;
    }

    // AI 理财知识库问答功能

    @Resource
    private VectorStore financeAppVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private FinanceAppRagCorpus financeAppRagCorpus;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId, long userId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        List<Document> retrievedDocuments = retrieveRagDocuments(rewrittenMessage);
        logRagRetrieval(rewrittenMessage, retrievedDocuments);
        String ragPrompt = buildRagPrompt(rewrittenMessage, retrievedDocuments);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(ragPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        content = appendRagReferences(content, retrievedDocuments);
        log.info("content: {}", content);
        return content;
    }

    private List<Document> retrieveRagDocuments(String query) {
        try {
            List<Document> candidates = new ArrayList<>();
            for (String searchQuery : expandRagQueries(query)) {
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(searchQuery)
                        .topK(8)
                        .similarityThreshold(0.22)
                        .build();
                List<Document> documents = financeAppVectorStore.similaritySearch(searchRequest);
                if (documents != null) {
                    candidates.addAll(documents);
                }
            }
            candidates.addAll(financeAppRagCorpus.keywordSearch(query, 10));
            return rerankRagDocuments(query, deduplicateRagDocuments(candidates), 6);
        } catch (Exception e) {
            log.warn("RAG document retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> expandRagQueries(String query) {
        return FinanceRagQueryPlanner.expandQueries(query, 3).stream().toList();
    }

    private List<Document> deduplicateRagDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Document> deduplicated = new ArrayList<>();
        for (Document document : documents) {
            String key = ragDocumentKey(document);
            if (seen.add(key)) {
                deduplicated.add(document);
            }
        }
        return deduplicated;
    }

    private List<Document> rerankRagDocuments(String query, List<Document> documents, int limit) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<ScoredDocument> scored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            scored.add(new ScoredDocument(document, lexicalFinanceScore(query, document), i));
        }
        return scored.stream()
                .sorted((left, right) -> {
                    int scoreCompare = Double.compare(right.score(), left.score());
                    return scoreCompare != 0 ? scoreCompare : Integer.compare(left.originalIndex(), right.originalIndex());
                })
                .map(ScoredDocument::document)
                .limit(limit)
                .toList();
    }

    private double lexicalFinanceScore(String query, Document document) {
        String q = query == null ? "" : query.toLowerCase();
        StringBuilder haystackBuilder = new StringBuilder(document.getText() == null ? "" : document.getText().toLowerCase());
        if (document.getMetadata() != null) {
            document.getMetadata().forEach((key, value) -> haystackBuilder
                    .append(' ')
                    .append(key)
                    .append(' ')
                    .append(value == null ? "" : value.toString().toLowerCase()));
        }
        String haystack = haystackBuilder.toString();
        double score = 0;
        for (String keyword : FinanceRagTerms.KEYWORDS) {
            String k = keyword.toLowerCase();
            if (q.contains(k) && haystack.contains(k)) {
                score += k.length() > 2 ? 2.0 : 1.2;
            }
        }
        for (String token : q.split("[^a-z0-9]+")) {
            if (token.length() >= 3 && haystack.contains(token)) {
                score += 0.8;
            }
        }
        score += FinanceRagQueryPlanner.metadataBoost(query, document);
        return score;
    }

    private void logRagRetrieval(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.info("RAG retrieved no documents for query: {}", query);
            return;
        }
        String summary = documents.stream()
                .map(document -> {
                    Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
                    return metadata.getOrDefault("category", "unknown") + "/"
                            + metadata.getOrDefault("assetClass", "unknown") + ":"
                            + getDocumentTitle(document);
                })
                .collect(Collectors.joining(" | "));
        log.info("RAG retrieved {} documents for query '{}': {}", documents.size(), query, summary);
    }

    private String ragDocumentKey(Document document) {
        if (document == null || document.getMetadata() == null) {
            return String.valueOf(document == null ? "" : document.getText());
        }
        Map<String, Object> metadata = document.getMetadata();
        return metadata.getOrDefault("chunkId", "") + "|"
                + metadata.getOrDefault("sourcePath", "") + "|"
                + metadata.getOrDefault("sectionIndex", "") + "|"
                + metadata.getOrDefault("pageStart", "") + "|"
                + metadata.getOrDefault("chunkIndex", "");
    }

    private record ScoredDocument(Document document, double score, int originalIndex) {
    }

    private String buildRagPrompt(String question, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return question + "\n\n请直接回答。如果知识库资料不足，请明确说明。";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
            context.append("[来源").append(i + 1).append("] ")
                    .append(getDocumentTitle(document))
                    .append("\n")
                    .append("分类: ").append(metadata.getOrDefault("category", "unknown"))
                    .append(" / 资产类别: ").append(metadata.getOrDefault("assetClass", "unknown"))
                    .append("\n")
                    .append(shorten(document.getText(), 900))
                    .append("\n\n");
        }

        return """
                请基于下方金融知识库资料回答用户问题。
                要求：
                1. 优先使用资料中的信息，资料不足时请明确说明。
                2. 回答要专业、简洁、有可执行建议。
                3. 涉及资料依据时，可在句末标注来源编号，例如 [来源1]。

                【用户问题】
                %s

                【知识库资料】
                %s
                """.formatted(question, context.toString().trim());
    }

    private String appendRagReferences(String content, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return content;
        }

        Set<String> sources = new LinkedHashSet<>();
        for (int i = 0; i < documents.size(); i++) {
            sources.add("[来源" + (i + 1) + "] " + getDocumentTitle(documents.get(i)));
        }

        return content + "\n\n---\n参考来源：\n" + sources.stream()
                .map(source -> "- " + source)
                .collect(Collectors.joining("\n"));
    }

    private String getDocumentTitle(Document document) {
        if (document == null) {
            return "未知文档";
        }
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return "金融知识库文档";
        }
        String title = String.valueOf(metadata.getOrDefault("title", metadata.getOrDefault("filename", "金融知识库文档")));
        Object pageStart = metadata.get("pageStart");
        Object pageEnd = metadata.get("pageEnd");
        if (pageStart != null && pageEnd != null) {
            return title + "（第 " + pageStart + "-" + pageEnd + " 页）";
        }
        Object sectionTitle = metadata.get("sectionTitle");
        if (sectionTitle != null && !sectionTitle.toString().isBlank() && !sectionTitle.toString().equals(title)) {
            return title + " - " + sectionTitle;
        }
        return title;
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 理财报告功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId, long userId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 理财报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId, long userId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId(userId, chatId)))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    private String scopedConversationId(long userId, String chatId) {
        return userId + ":" + chatId;
    }
}
