package com.aiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSafetyTest {

    @Test
    void terminalToolBlocksDangerousCommands() {
        TerminalOperationTool tool = new TerminalOperationTool();

        String result = tool.executeTerminalCommand("ls; rm -rf tmp");

        assertTrue(result.contains("Command not allowed"));
    }

    @Test
    void terminalToolAllowsReadOnlyCommands() {
        TerminalOperationTool tool = new TerminalOperationTool();

        String result = tool.executeTerminalCommand("pwd");

        assertTrue(result.contains("ai-agent"));
    }

    @Test
    void fileToolBlocksPathTraversal() {
        FileOperationTool tool = new FileOperationTool();

        String result = tool.readFile("../application.yml");

        assertTrue(result.contains("Invalid file name"));
    }

    @Test
    void downloadToolBlocksLocalhostUrls() {
        ResourceDownloadTool tool = new ResourceDownloadTool();

        String result = tool.downloadResource("http://localhost:8123/api/health", "health.txt");

        assertTrue(result.contains("Invalid or unsafe URL"));
    }

    @Test
    void downloadToolBlocksUnsafeFileNames() {
        ResourceDownloadTool tool = new ResourceDownloadTool();

        String result = tool.downloadResource("https://example.com/file.txt", "../file.txt");

        assertTrue(result.contains("Invalid file name"));
    }
}
