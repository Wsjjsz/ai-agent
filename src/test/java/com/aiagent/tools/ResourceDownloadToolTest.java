package com.aiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceDownloadToolTest {

    @Test
    public void testDownloadResourceRejectsUnsafeLocalUrl() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "http://localhost:8123/api/health";
        String fileName = "logo.png";
        String result = tool.downloadResource(url, fileName);
        assertTrue(result.contains("Invalid or unsafe URL"));
    }
}
