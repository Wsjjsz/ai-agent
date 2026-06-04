package com.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.aiagent.config.UrlSafety;
import com.aiagent.files.GeneratedFileContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 资源下载工具
 */
public class ResourceDownloadTool {

    private static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 15;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url,
                                   @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        // 安全校验：验证文件名
        if (!isValidFileName(fileName)) {
            return "Error: Invalid file name. File name cannot contain path separators or special characters.";
        }

        // 安全校验：验证 URL
        if (!UrlSafety.isSafeHttpUrl(url)) {
            return "Error: Invalid or unsafe URL. Only HTTP/HTTPS URLs are allowed, and internal network addresses are blocked.";
        }

        Path target = GeneratedFileContext.resolve("download", fileName);
        String filePath = target.toString();
        try {
            URI safeUri = UrlSafety.requireSafeHttpUrl(url);
            HttpRequest request = HttpRequest.newBuilder(safeUri)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                return "Error downloading resource: redirects are not allowed for safety.";
            }
            if (status < 200 || status >= 300) {
                return "Error downloading resource: remote server returned HTTP " + status;
            }

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                return "Error downloading resource: file is larger than 20MB.";
            }

            FileUtil.mkdir(target.getParent().toString());
            copyWithLimit(response.body(), target);
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }

    private void copyWithLimit(InputStream inputStream, Path target) throws Exception {
        try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) {
                    Files.deleteIfExists(target);
                    throw new IllegalArgumentException("file is larger than 20MB");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * 验证文件名是否合法
     */
    private boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含路径分隔符
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }

        // 检查是否包含非法字符
        if (fileName.contains("\0") || fileName.contains(":") || fileName.contains("*") ||
            fileName.contains("?") || fileName.contains("\"") || fileName.contains("<") ||
            fileName.contains(">") || fileName.contains("|")) {
            return false;
        }

        return true;
    }
}
