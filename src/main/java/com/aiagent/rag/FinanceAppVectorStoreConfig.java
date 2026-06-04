package com.aiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 理财大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Configuration
@ConditionalOnProperty(name = "app.rag.vector-store", havingValue = "simple", matchIfMissing = true)
@Slf4j
public class FinanceAppVectorStoreConfig {

    @Resource
    private FinanceAppDocumentLoader financeAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private FinanceAppRagCorpus financeAppRagCorpus;

    @Value("${app.rag.keyword-enrichment.enabled:false}")
    private boolean keywordEnrichmentEnabled;

    @Value("${app.rag.index-on-startup:true}")
    private boolean indexOnStartup;

    @Bean
    VectorStore financeAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        if (!indexOnStartup) {
            log.info("RAG vector store startup indexing is disabled");
            return simpleVectorStore;
        }

        // 加载 Markdown + PDF 资料，并按 RAG 检索粒度切分
        List<Document> documentList = financeAppDocumentLoader.loadDocuments();
        List<Document> documentsToIndex = myTokenTextSplitter.splitForRag(documentList);
        financeAppRagCorpus.replaceAll(documentsToIndex);
        log.info("RAG source documents: {}, indexed chunks after split: {}", documentList.size(), documentsToIndex.size());
        if (keywordEnrichmentEnabled) {
            // 自动补充关键词元信息；默认关闭，避免启动期依赖大模型额度。
            documentsToIndex = myKeywordEnricher.enrichDocuments(documentsToIndex);
        } else {
            log.info("RAG keyword metadata enrichment is disabled");
        }

        try {
            simpleVectorStore.add(documentsToIndex);
            log.info("RAG vector store initialized with {} documents", documentsToIndex.size());
        } catch (Exception e) {
            log.warn("RAG vector store initialization failed, application will continue without indexed documents: {}", e.getMessage());
        }
        return simpleVectorStore;
    }
}
