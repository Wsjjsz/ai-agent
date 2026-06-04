package com.aiagent.controller;

import com.aiagent.agent.YuManus;
import com.aiagent.app.FinanceApp;
import com.aiagent.app.FinanceAppService;
import com.aiagent.auth.AuthContext;
import com.aiagent.auth.AuthenticatedUser;
import com.aiagent.auth.GuestQuotaService;
import com.aiagent.config.AiRequestLimiter;
import com.aiagent.constant.FileConstant;
import com.aiagent.files.FileAccessTokenService;
import com.aiagent.files.GeneratedFileContext;
import com.aiagent.history.ChatHistoryRepository;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    private static final String FILE_PREVIEW_CSP = "default-src 'none'; img-src 'self' data: https: http:; "
            + "style-src 'self' 'unsafe-inline'; font-src 'self' data:; object-src 'none'; "
            + "base-uri 'none'; frame-ancestors 'self'";
    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    @Resource
    private FinanceAppService financeApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ChatHistoryRepository chatHistoryRepository;

    @Resource
    private GuestQuotaService guestQuotaService;

    @Resource
    private FileAccessTokenService fileAccessTokenService;

    @Resource
    private AiRequestLimiter aiRequestLimiter;

    // ==================== 基础对话接口 ====================

    /**
     * 同步调用 AI 理财大师应用
     */
    @PostMapping("/finance_app/chat/sync")
    public String doChatWithFinanceAppSync(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSlot(() -> financeApp.doChat(message, chatId, user.id()));
    }

    /**
     * SSE 流式调用 AI 理财大师应用
     */
    @PostMapping(value = "/finance_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithFinanceAppSSE(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiFlux(() -> financeApp.doChatByStream(message, chatId, user.id()));
    }

    /**
     * SSE 流式调用 AI 理财大师应用（ServerSentEvent 格式）
     */
    @PostMapping(value = "/finance_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithFinanceAppServerSentEvent(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiFlux(() -> financeApp.doChatByStream(message, chatId, user.id())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build()));
    }

    /**
     * SSE 流式调用 AI 理财大师应用（SseEmitter 格式）
     */
    @PostMapping(value = "/finance_app/chat/sse_emitter")
    public SseEmitter doChatWithFinanceAppServerSseEmitter(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSse(() -> {
            // 创建一个超时时间较长的 SseEmitter
            SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
            // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
            financeApp.doChatByStream(message, chatId, user.id())
                    .subscribe(chunk -> {
                        try {
                            sseEmitter.send(chunk);
                        } catch (IOException e) {
                            sseEmitter.completeWithError(e);
                        }
                    }, sseEmitter::completeWithError, sseEmitter::complete);
            // 返回
            return sseEmitter;
        });
    }

    // ==================== 结构化输出接口 ====================

    /**
     * AI 理财报告功能（结构化输出）
     */
    @PostMapping("/finance_app/chat/report")
    public FinanceApp.FinanceReport doChatWithReport(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSlot(() -> financeApp.doChatWithReport(message, chatId, user.id()));
    }

    // ==================== RAG 知识库接口 ====================

    /**
     * AI 理财知识库问答（RAG）
     */
    @PostMapping("/finance_app/chat/rag")
    public String doChatWithRag(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSlot(() -> financeApp.doChatWithRag(message, chatId, user.id()));
    }

    // ==================== 工具调用接口 ====================

    /**
     * AI 理财对话（支持工具调用）
     */
    @PostMapping("/finance_app/chat/tools")
    public String doChatWithTools(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSlot(() -> financeApp.doChatWithTools(message, chatId, user.id()));
    }

    // ==================== MCP 服务接口 ====================

    /**
     * AI 理财对话（调用 MCP 服务）
     */
    @PostMapping("/finance_app/chat/mcp")
    public String doChatWithMcp(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        return withAiSlot(() -> financeApp.doChatWithMcp(message, chatId, user.id()));
    }

    // ==================== Manus 超级智能体接口 ====================

    /**
     * 流式调用 Manus 超级智能体
     */
    @PostMapping("/manus/chat")
    public SseEmitter doChatWithManus(HttpServletRequest servletRequest, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String chatId = request.get("chatId");
        AuthenticatedUser user = requireChatOwner(servletRequest, chatId);
        String outputFormat = request.get("outputFormat");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be empty");
        }
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        String finalPrompt = message.trim();
        if (outputFormat != null && !outputFormat.isBlank()) {
            finalPrompt += "\n\n【输出要求】\n" + outputFormat.trim();
        }
        String context = buildConversationContext(user.id(), chatId);
        if (!context.isEmpty()) {
            finalPrompt = "【历史对话记录】\n" + context + "\n\n【当前问题】\n" + finalPrompt;
        }
        String prompt = finalPrompt;

        GeneratedFileContext.set(new GeneratedFileContext.Scope(user.id(), chatId));
        try {
            return withAiSse(() -> yuManus.runStream(prompt, assistantContent ->
                    persistManusConversation(user.id(), chatId, message.trim(), assistantContent)));
        } finally {
            GeneratedFileContext.clear();
        }
    }

    private void persistManusConversation(long userId, String chatId, String userMessage, String assistantContent) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        try {
            chatHistoryRepository.addMessage(chatId, userId, "user", userMessage);
            chatHistoryRepository.addMessage(chatId, userId, "assistant", assistantContent);
        } catch (Exception e) {
            log.warn("Failed to persist Manus conversation for chatId={}: {}", chatId, e.getMessage());
        }
    }

    private String buildConversationContext(long userId, String chatId) {
        try {
            java.util.List<java.util.Map<String, Object>> messages = chatHistoryRepository.listMessages(chatId, userId);
            if (messages == null || messages.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (java.util.Map<String, Object> msg : messages) {
                String role = (String) msg.get("role");
                String content = (String) msg.get("content");
                if (content == null || content.isBlank()) continue;
                if ("user".equals(role)) {
                    sb.append("用户: ").append(content.length() > 500 ? content.substring(0, 500) + "..." : content).append("\n");
                } else if ("assistant".equals(role)) {
                    String summary = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                    sb.append("助手: ").append(summary).append("\n");
                }
                count++;
            }
            return count > 0 ? sb.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 读取 Manus 最终整理输出文件
     */
    @GetMapping(value = "/manus/result", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getManusResult(HttpServletRequest servletRequest, @RequestParam(required = false) String fileName) throws IOException {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        Path userRoot = GeneratedFileContext.userRoot(user.id());
        if (fileName == null || fileName.isBlank()) {
            return readLatestTxt(userRoot);
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
        }
        Path filePath = findUserFileByName(userRoot, fileName);
        if (filePath == null || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取 Manus 最新整理输出文件
     */
    @GetMapping(value = "/manus/result/latest", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLatestManusResult(HttpServletRequest servletRequest) throws IOException {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        return readLatestTxt(GeneratedFileContext.userRoot(user.id()));
    }

    private String readLatestTxt(Path userRoot) throws IOException {
        if (!Files.exists(userRoot) || !Files.isDirectory(userRoot)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "结果目录不存在");
        }

        try (Stream<Path> pathStream = Files.walk(userRoot, 5)) {
            Optional<Path> latestFile = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".txt") || name.endsWith(".md");
                    })
                    .max(Comparator.comparing(path -> {
                        try {
                            FileTime time = Files.getLastModifiedTime(path);
                            return time.toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }));

            if (latestFile.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无结果文件");
            }

            return Files.readString(latestFile.get(), StandardCharsets.UTF_8);
        }
    }

    // ==================== 文件下载接口 ====================

    @GetMapping("/manus/file/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(HttpServletRequest servletRequest,
                                                                             @RequestParam String path,
                                                                             @RequestParam(name = "file_token", required = false) String fileToken) throws IOException {
        long userId = resolveFileAccessUserId(servletRequest, path, "download", fileToken);
        Path filePath = resolveGeneratedFile(path, userId);
        MediaType mediaType = resolveFileMediaType(filePath);

        InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
                .header(CONTENT_SECURITY_POLICY_HEADER, "sandbox; default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }

    @GetMapping("/manus/file/preview")
    public ResponseEntity<org.springframework.core.io.Resource> previewFile(HttpServletRequest servletRequest,
                                                                            @RequestParam String path,
                                                                            @RequestParam(name = "file_token", required = false) String fileToken) throws IOException {
        long userId = resolveFileAccessUserId(servletRequest, path, "preview", fileToken);
        Path filePath = resolveGeneratedFile(path, userId);
        MediaType mediaType = resolveFileMediaType(filePath);

        InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
                .header(CONTENT_SECURITY_POLICY_HEADER, FILE_PREVIEW_CSP)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }

    @GetMapping("/manus/file/sign")
    public Map<String, Object> signFileUrl(HttpServletRequest servletRequest,
                                           @RequestParam String path,
                                           @RequestParam(defaultValue = "preview") String disposition) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        String normalizedDisposition = "download".equalsIgnoreCase(disposition) ? "download" : "preview";
        resolveGeneratedFile(path, user.id());
        String token = fileAccessTokenService.create(user.id(), path, normalizedDisposition);
        String endpoint = "download".equals(normalizedDisposition) ? "download" : "preview";
        String url = "/api/ai/manus/file/" + endpoint
                + "?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8)
                + "&file_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return Map.of("url", url, "disposition", normalizedDisposition);
    }

    private long resolveFileAccessUserId(HttpServletRequest servletRequest, String path, String disposition, String fileToken) {
        if (fileToken != null && !fileToken.isBlank()) {
            try {
                return fileAccessTokenService.verify(fileToken, path, disposition).userId();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "文件访问链接已失效，请重新打开");
            }
        }
        return AuthContext.requireUser(servletRequest).id();
    }

    private Path resolveGeneratedFile(String path, long userId) {
        Path baseDir = Paths.get(FileConstant.FILE_SAVE_DIR).toAbsolutePath().normalize();
        Path userRoot = GeneratedFileContext.userRoot(userId);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件目录不存在");
        }

        if (path == null || path.isBlank()
                || path.contains("..") || path.contains("~")
                || path.contains("\0") || path.contains(":") || path.contains("*")
                || path.contains("?") || path.contains("\"") || path.contains("<")
                || path.contains(">") || path.contains("|")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
        }

        Path requested = Paths.get(path);
        Path filePath = (requested.isAbsolute() ? requested : baseDir.resolve(requested)).toAbsolutePath().normalize();
        if (!filePath.startsWith(userRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return filePath;
    }

    private MediaType resolveFileMediaType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return MediaType.TEXT_HTML;
        }
        if (fileName.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".csv")) {
            return MediaType.valueOf("text/csv");
        }
        if (fileName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (fileName.endsWith(".docx")) {
            return MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (fileName.endsWith(".xlsx")) {
            return MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (fileName.endsWith(".pptx")) {
            return MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
        if (fileName.endsWith(".md") || fileName.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping("/manus/files")
    public List<Map<String, Object>> listGeneratedFiles(HttpServletRequest servletRequest) throws IOException {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        Path baseDir = Paths.get(FileConstant.FILE_SAVE_DIR).toAbsolutePath().normalize();
        Path userRoot = GeneratedFileContext.userRoot(user.id());
        List<Map<String, Object>> result = new ArrayList<>();
        if (!Files.exists(userRoot) || !Files.isDirectory(userRoot)) {
            return result;
        }

        try (Stream<Path> fileStream = Files.walk(userRoot, 5)) {
            fileStream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", file.getFileName().toString());
                    info.put("path", baseDir.relativize(file).toString().replace("\\", "/"));
                    info.put("size", Files.size(file));
                    info.put("lastModified", Files.getLastModifiedTime(file).toMillis());
                    result.add(info);
                } catch (IOException ignored) {}
            });
        }
        result.sort((a, b) -> Long.compare((long) b.get("lastModified"), (long) a.get("lastModified")));
        return result;
    }

    private Path findUserFileByName(Path userRoot, String fileName) throws IOException {
        if (!Files.exists(userRoot) || !Files.isDirectory(userRoot)) {
            return null;
        }
        try (Stream<Path> pathStream = Files.walk(userRoot, 5)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private AuthenticatedUser requireChatOwner(HttpServletRequest request, String chatId) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        if (chatId == null || chatId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId cannot be empty");
        }
        if (!chatHistoryRepository.ownsSession(chatId, user.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        guestQuotaService.consumeIfGuest(user, request);
        return user;
    }

    private <T> T withAiSlot(Supplier<T> supplier) {
        return aiRequestLimiter == null ? supplier.get() : aiRequestLimiter.call(supplier);
    }

    private <T> Flux<T> withAiFlux(Supplier<Flux<T>> supplier) {
        return aiRequestLimiter == null ? supplier.get() : aiRequestLimiter.flux(supplier);
    }

    private SseEmitter withAiSse(Supplier<SseEmitter> supplier) {
        return aiRequestLimiter == null ? supplier.get() : aiRequestLimiter.sse(supplier);
    }

}
