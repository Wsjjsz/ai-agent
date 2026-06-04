package com.aiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UrlSafetyTest {

    @Test
    void blocksLocalhostAndNonHttpUrls() {
        assertFalse(UrlSafety.isSafeHttpUrl("http://localhost:8123/api/health"));
        assertFalse(UrlSafety.isSafeHttpUrl("http://127.0.0.1:8123/api/health"));
        assertFalse(UrlSafety.isSafeHttpUrl("ftp://example.com/file.txt"));
    }
}
