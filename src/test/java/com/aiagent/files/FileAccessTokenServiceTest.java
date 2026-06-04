package com.aiagent.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileAccessTokenServiceTest {

    @Test
    void tokenIsScopedToUserPathAndDisposition() {
        FileAccessTokenService service = new FileAccessTokenService("test-secret", 60);

        String token = service.create(12L, "file/report.pdf", "preview");
        FileAccessTokenService.FileAccessGrant grant = service.verify(token, "file/report.pdf", "preview");

        assertEquals(12L, grant.userId());
        assertEquals("file/report.pdf", grant.path());
        assertEquals("preview", grant.disposition());
        assertThrows(IllegalArgumentException.class, () -> service.verify(token, "file/other.pdf", "preview"));
        assertThrows(IllegalArgumentException.class, () -> service.verify(token, "file/report.pdf", "download"));
    }
}
