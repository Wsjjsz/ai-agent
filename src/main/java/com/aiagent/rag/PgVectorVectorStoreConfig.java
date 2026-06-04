package com.aiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
@ConditionalOnProperty(name = "app.rag.vector-store", havingValue = "pgvector")
@Slf4j
public class PgVectorVectorStoreConfig {

    private static final int DASHSCOPE_EMBEDDING_BATCH_LIMIT = 25;

    @Resource
    private FinanceAppDocumentLoader financeAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private FinanceAppRagCorpus financeAppRagCorpus;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private RagIndexManifestRepository ragIndexManifestRepository;

    @Value("${app.rag.keyword-enrichment.enabled:false}")
    private boolean keywordEnrichmentEnabled;

    @Value("${app.rag.index-on-startup:true}")
    private boolean indexOnStartup;

    @Value("${app.rag.incremental.adopt-existing-without-manifest:true}")
    private boolean adoptExistingWithoutManifest;

    @Value("${spring.ai.dashscope.embedding.options.model:dashscope-default-1536}")
    private String embeddingModelName;

    @Bean("financeAppVectorStore")
    public VectorStore financeAppVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        if (!indexOnStartup) {
            log.info("PgVector RAG startup indexing is disabled");
            return vectorStore;
        }

        // 加载 Markdown + PDF 资料，并按 RAG 检索粒度切分
        List<Document> documentList = financeAppDocumentLoader.loadDocuments();
        List<Document> documents = myTokenTextSplitter.splitForRag(documentList);
        financeAppRagCorpus.replaceAll(documents);
        log.info("PgVector RAG source documents: {}, indexed chunks after split: {}", documentList.size(), documents.size());
        if (keywordEnrichmentEnabled) {
            documents = myKeywordEnricher.enrichDocuments(documents);
        } else {
            log.info("PgVector RAG keyword metadata enrichment is disabled");
        }
        try {
            incrementalIndex(vectorStore, documents);
        } catch (Exception e) {
            log.warn("PgVector RAG vector store initialization failed, application will continue without indexed documents: {}", e.getMessage());
        }
        return vectorStore;
    }

    private void incrementalIndex(VectorStore vectorStore, List<Document> documents) {
        Map<String, SourceDocuments> currentSources = groupBySourcePath(documents);
        Map<String, RagIndexManifestRepository.Entry> existingManifests = ragIndexManifestRepository.findAll();
        Set<String> currentSourcePaths = currentSources.keySet();
        int removedSources = removeDeletedSources(existingManifests.keySet(), currentSourcePaths);

        int adoptedSources = 0;
        int skippedSources = 0;
        int indexedSources = 0;
        int indexedChunks = 0;
        for (SourceDocuments source : currentSources.values()) {
            RagIndexManifestRepository.Entry existing = existingManifests.get(source.sourcePath());
            if (existing != null && existing.matches(source.fileHash(), source.documents().size(),
                    embeddingModelName, MyTokenTextSplitter.VERSION)) {
                skippedSources++;
                continue;
            }

            if (existing == null && adoptExistingWithoutManifest
                    && ragIndexManifestRepository.countVectorRows(source.sourcePath()) == source.documents().size()) {
                ragIndexManifestRepository.upsert(source.sourcePath(), source.fileHash(), source.documentType(),
                        source.documents().size(), embeddingModelName, MyTokenTextSplitter.VERSION);
                adoptedSources++;
                log.info("PgVector RAG adopted existing vectors for unchanged source: {}", source.sourcePath());
                continue;
            }

            int deletedRows = ragIndexManifestRepository.deleteVectorRows(source.sourcePath());
            addDocumentsInBatches(vectorStore, source.documents());
            ragIndexManifestRepository.upsert(source.sourcePath(), source.fileHash(), source.documentType(),
                    source.documents().size(), embeddingModelName, MyTokenTextSplitter.VERSION);
            indexedSources++;
            indexedChunks += source.documents().size();
            log.info("PgVector RAG indexed source: {}, chunks: {}, old rows deleted: {}",
                    source.sourcePath(), source.documents().size(), deletedRows);
        }
        log.info("PgVector RAG incremental index completed. sources={}, indexedSources={}, indexedChunks={}, adoptedSources={}, skippedSources={}, removedSources={}",
                currentSources.size(), indexedSources, indexedChunks, adoptedSources, skippedSources, removedSources);
    }

    private int removeDeletedSources(Set<String> existingSourcePaths, Set<String> currentSourcePaths) {
        int removed = 0;
        for (String sourcePath : existingSourcePaths) {
            if (currentSourcePaths.contains(sourcePath)) {
                continue;
            }
            int deletedRows = ragIndexManifestRepository.deleteVectorRows(sourcePath);
            ragIndexManifestRepository.delete(sourcePath);
            removed++;
            log.info("PgVector RAG removed stale source: {}, vector rows deleted: {}", sourcePath, deletedRows);
        }
        return removed;
    }

    private Map<String, SourceDocuments> groupBySourcePath(List<Document> documents) {
        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document document : documents) {
            String sourcePath = metadataValue(document, "sourcePath");
            if (sourcePath.isBlank()) {
                sourcePath = "unknown:" + Objects.hashCode(document.getText());
            }
            grouped.computeIfAbsent(sourcePath, ignored -> new ArrayList<>()).add(document);
        }

        Map<String, SourceDocuments> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            List<Document> sourceDocuments = entry.getValue();
            Document first = sourceDocuments.get(0);
            String fileHash = metadataValue(first, "fileHash");
            if (fileHash.isBlank()) {
                fileHash = Integer.toHexString(Objects.hash(sourceDocuments.stream()
                        .map(Document::getText)
                        .toList()));
            }
            String documentType = metadataValue(first, "documentType");
            if (documentType.isBlank()) {
                documentType = "unknown";
            }
            result.put(entry.getKey(), new SourceDocuments(entry.getKey(), fileHash, documentType, sourceDocuments));
        }
        return result;
    }

    private String metadataValue(Document document, String key) {
        if (document == null || document.getMetadata() == null) {
            return "";
        }
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }

    private void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents) {
        for (int start = 0; start < documents.size(); start += DASHSCOPE_EMBEDDING_BATCH_LIMIT) {
            int end = Math.min(start + DASHSCOPE_EMBEDDING_BATCH_LIMIT, documents.size());
            vectorStore.add(documents.subList(start, end));
            log.info("PgVector RAG indexed batch {}/{} documents", end, documents.size());
        }
    }

    private record SourceDocuments(
            String sourcePath,
            String fileHash,
            String documentType,
            List<Document> documents
    ) {
    }
}
