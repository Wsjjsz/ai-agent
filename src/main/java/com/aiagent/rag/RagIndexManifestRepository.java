package com.aiagent.rag;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RagIndexManifestRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagIndexManifestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Entry> findAll() {
        List<Entry> entries = jdbcTemplate.query("""
                        SELECT source_path, file_hash, document_type, chunk_count, embedding_model, splitter_version
                        FROM rag_index_manifest
                        """,
                (rs, rowNum) -> new Entry(
                        rs.getString("source_path"),
                        rs.getString("file_hash"),
                        rs.getString("document_type"),
                        rs.getInt("chunk_count"),
                        rs.getString("embedding_model"),
                        rs.getString("splitter_version")
                ));
        Map<String, Entry> result = new HashMap<>();
        for (Entry entry : entries) {
            result.put(entry.sourcePath(), entry);
        }
        return result;
    }

    public int countVectorRows(String sourcePath) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'sourcePath' = ?",
                    Integer.class,
                    sourcePath
            );
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    public int deleteVectorRows(String sourcePath) {
        try {
            return jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata ->> 'sourcePath' = ?",
                    sourcePath
            );
        } catch (DataAccessException e) {
            return 0;
        }
    }

    public void upsert(String sourcePath, String fileHash, String documentType, int chunkCount,
                       String embeddingModel, String splitterVersion) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                        INSERT INTO rag_index_manifest
                            (source_path, file_hash, document_type, chunk_count, embedding_model, splitter_version, indexed_at, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (source_path) DO UPDATE SET
                            file_hash = EXCLUDED.file_hash,
                            document_type = EXCLUDED.document_type,
                            chunk_count = EXCLUDED.chunk_count,
                            embedding_model = EXCLUDED.embedding_model,
                            splitter_version = EXCLUDED.splitter_version,
                            update_time = EXCLUDED.update_time
                        """,
                sourcePath,
                fileHash,
                documentType,
                chunkCount,
                embeddingModel,
                splitterVersion,
                now,
                now
        );
    }

    public void delete(String sourcePath) {
        jdbcTemplate.update("DELETE FROM rag_index_manifest WHERE source_path = ?", sourcePath);
    }

    public record Entry(
            String sourcePath,
            String fileHash,
            String documentType,
            int chunkCount,
            String embeddingModel,
            String splitterVersion
    ) {
        public boolean matches(String currentFileHash, int currentChunkCount,
                               String currentEmbeddingModel, String currentSplitterVersion) {
            return fileHash.equals(currentFileHash)
                    && chunkCount == currentChunkCount
                    && embeddingModel.equals(currentEmbeddingModel)
                    && splitterVersion.equals(currentSplitterVersion);
        }
    }
}
