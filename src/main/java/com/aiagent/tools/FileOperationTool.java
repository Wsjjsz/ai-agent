package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.aiagent.files.GeneratedFileContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件操作工具类（提供文件读写功能）
 */
@Component
public class FileOperationTool {

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        // 安全校验：防止路径遍历攻击
        String filePath = validateAndResolvePath(fileName);
        if (filePath == null) {
            return "Error: Invalid file name. Path traversal is not allowed.";
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return "Error: File not found: " + fileName;
            }
            if (!file.isFile()) {
                return "Error: Not a file: " + fileName;
            }
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        // 安全校验：防止路径遍历攻击
        String filePath = validateAndResolvePath(fileName);
        if (filePath == null) {
            return "Error: Invalid file name. Path traversal is not allowed.";
        }

        try {
            // 创建目录
            FileUtil.mkdir(fileDir());
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    /**
     * 验证文件名并解析路径，防止路径遍历攻击
     * @param fileName 文件名
     * @return 解析后的安全路径，如果无效则返回 null
     */
    private String validateAndResolvePath(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        // 检查是否包含路径遍历字符
        if (fileName.contains("..") || fileName.contains("~") || fileName.startsWith("/")) {
            return null;
        }

        // 检查是否包含非法字符
        if (fileName.contains("\0") || fileName.contains(":") || fileName.contains("*") ||
            fileName.contains("?") || fileName.contains("\"") || fileName.contains("<") ||
            fileName.contains(">") || fileName.contains("|")) {
            return null;
        }

        try {
            // 规范化路径
            Path basePath = Paths.get(fileDir()).toAbsolutePath().normalize();
            Path resolvedPath = basePath.resolve(fileName).normalize();

            // 确保解析后的路径在允许的目录内
            if (!resolvedPath.startsWith(basePath)) {
                return null;
            }

            return resolvedPath.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String fileDir() {
        return GeneratedFileContext.baseDir().resolve("file").toAbsolutePath().normalize().toString();
    }
}
