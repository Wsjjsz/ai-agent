package com.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class FinanceAppDocumentLoaderTest {

    @Test
    void loadMarkdowns() {
        FinanceAppDocumentLoader financeAppDocumentLoader =
                new FinanceAppDocumentLoader(new PathMatchingResourcePatternResolver());
        assertFalse(financeAppDocumentLoader.loadMarkdowns().isEmpty());
    }

    @Test
    void loadDocumentsWithLimitedPdfResources() {
        FinanceAppDocumentLoader financeAppDocumentLoader =
                new FinanceAppDocumentLoader(new PathMatchingResourcePatternResolver());
        ReflectionTestUtils.setField(financeAppDocumentLoader, "pdfEnabled", true);
        ReflectionTestUtils.setField(financeAppDocumentLoader, "maxPdfDocuments", 1);
        ReflectionTestUtils.setField(financeAppDocumentLoader, "maxPdfPagesPerDocument", 2);
        ReflectionTestUtils.setField(financeAppDocumentLoader, "maxPdfCharsPerDocument", 6000);

        assertTrue(financeAppDocumentLoader.loadDocuments().stream()
                .anyMatch(document -> "pdf".equals(document.getMetadata().get("documentType"))));
    }
}
