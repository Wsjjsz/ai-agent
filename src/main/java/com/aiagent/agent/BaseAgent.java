package com.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiagent.agent.model.AgentState;
import com.aiagent.files.GeneratedFileContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态（使用 volatile 保证多线程可见性）
    private volatile AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    private transient SseEmitter streamEmitter;

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        return runStream(userPrompt, null);
    }

    /**
     * 运行代理（流式输出），完成后可回调序列化后的推理内容用于持久化。
     *
     * @param userPrompt 用户提示词
     * @param completionHandler 成功生成最终结果后的回调
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt, Consumer<String> completionHandler) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        GeneratedFileContext.Scope generatedFileScope = GeneratedFileContext.current();
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            GeneratedFileContext.set(generatedFileScope);
            this.streamEmitter = sseEmitter;
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sendAgentEvent(sseEmitter, "error", null, "错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sendAgentEvent(sseEmitter, "error", null, "错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    // 输出当前每一步的结果到 SSE
                    sendAgentEvent(sseEmitter, "step", stepNumber, stepResult);
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sendAgentEvent(sseEmitter, "status", currentStep, "执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 发送最终整理结果给前端，避免前端再读文件。
                String finalSummary = extractFinalSummary(results, messageList);
                String finalSummaryMarkdown = buildFinalSummaryMarkdown(userPrompt, finalSummary, results);
                persistFinalSummaryMarkdown(finalSummaryMarkdown);
                notifyCompletion(completionHandler, serializeAgentTrace(results, finalSummaryMarkdown));
                if (StrUtil.isNotBlank(finalSummaryMarkdown)) {
                    sendAgentEvent(sseEmitter, "final", null, finalSummaryMarkdown);
                }
                // 发送结束标记，便于前端识别流式响应的正常结束。
                sendAgentEvent(sseEmitter, "done", null, "");
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sendAgentEvent(sseEmitter, "error", null, "执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.streamEmitter = null;
                this.cleanup();
                GeneratedFileContext.clear();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    protected void emitAgentEvent(String type, String title, String status, String content) {
        if (this.streamEmitter == null) {
            return;
        }
        try {
            sendAgentEvent(this.streamEmitter, type, currentStep > 0 ? currentStep : null, title, status, content, null);
        } catch (Exception e) {
            log.warn("Failed to emit agent event: {}", e.getMessage());
        }
    }

    private void sendAgentEvent(SseEmitter sseEmitter, String type, Integer stepNo, String content) throws IOException {
        sendAgentEvent(sseEmitter, type, stepNo, null, null, content, null);
    }

    private void sendAgentEvent(SseEmitter sseEmitter, String type, Integer stepNo, String title,
                                String status, String content, Object data) throws IOException {
        AgentStreamEvent event = new AgentStreamEvent(type, stepNo, title, status, content, data);
        sseEmitter.send(OBJECT_MAPPER.writeValueAsString(event));
    }

    private record AgentStreamEvent(String type, Integer stepNo, String title, String status, String content, Object data) {
    }

    private void notifyCompletion(Consumer<String> completionHandler, String serializedContent) {
        if (completionHandler == null || StrUtil.isBlank(serializedContent)) {
            return;
        }
        try {
            completionHandler.accept(serializedContent);
        } catch (Exception e) {
            log.warn("Failed to run agent completion handler", e);
        }
    }

    private String serializeAgentTrace(List<String> results, String finalSummaryMarkdown) {
        StringBuilder sb = new StringBuilder();
        if (results != null) {
            for (String result : results) {
                if (StrUtil.isNotBlank(result)) {
                    sb.append(result.trim()).append("\n");
                }
            }
        }
        if (StrUtil.isNotBlank(finalSummaryMarkdown)) {
            sb.append("[FINAL_RESULT]").append(finalSummaryMarkdown.trim());
        }
        return sb.toString().trim();
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }

    private String extractFinalSummary(List<String> results, List<Message> conversationMessages) {
        String summaryFromAssistantText = extractSummaryFromAssistantText(conversationMessages);
        if (StrUtil.isNotBlank(summaryFromAssistantText)) {
            return summaryFromAssistantText;
        }

        if (results == null || results.isEmpty()) {
            return "";
        }

        for (int i = results.size() - 1; i >= 0; i--) {
            String step = results.get(i);
            if (StrUtil.isBlank(step)) {
                continue;
            }
            String cleaned = step.replaceFirst("^Step\\s+\\d+:\\s*", "").trim();
            if (StrUtil.isBlank(cleaned)) {
                continue;
            }
            if (cleaned.contains("任务结束")
                    || cleaned.contains("doTerminate")
                    || cleaned.contains("Terminated: Reached max steps")) {
                continue;
            }
            return cleaned;
        }

        String fallback = results.get(results.size() - 1);
        return fallback == null ? "" : fallback.replaceFirst("^Step\\s+\\d+:\\s*", "").trim();
    }

    private String extractSummaryFromAssistantText(List<Message> conversationMessages) {
        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return "";
        }
        for (int i = conversationMessages.size() - 1; i >= 0; i--) {
            Message message = conversationMessages.get(i);
            if (!(message instanceof AssistantMessage assistantMessage)) {
                continue;
            }
            String text = StrUtil.trim(assistantMessage.getText());
            if (StrUtil.isBlank(text)) {
                continue;
            }
            if (text.contains("任务结束") || text.contains("doTerminate")) {
                continue;
            }
            return text;
        }
        return "";
    }

    private String buildFinalSummaryMarkdown(String userPrompt, String finalSummary, List<String> results) {
        String safePrompt = StrUtil.blankToDefault(userPrompt, "（无）").trim();
        String safeSummary = StrUtil.blankToDefault(finalSummary, "未生成明确总结。").trim();

        StringBuilder sb = new StringBuilder();
        sb.append("# 智能体整理结果\n\n");
        sb.append("## 用户问题\n");
        sb.append(safePrompt).append("\n\n");
        sb.append("## 最终总结\n");
        sb.append(safeSummary).append("\n\n");

        sb.append("## 生成时间\n");
        sb.append(LocalDateTime.now(REPORT_ZONE).format(REPORT_TIME_FORMATTER)).append("\n");
        return sb.toString();
    }

    private void persistFinalSummaryMarkdown(String markdownContent) {
        if (StrUtil.isBlank(markdownContent)) {
            return;
        }
        try {
            Path dir = GeneratedFileContext.baseDir().resolve("file");
            Files.createDirectories(dir);
            String filename = "manus_summary_" + LocalDateTime.now(REPORT_ZONE).format(FILE_TIME_FORMATTER) + ".md";
            Path target = dir.resolve(filename);
            Files.writeString(target, markdownContent, StandardCharsets.UTF_8);
            log.info("Persisted manus summary markdown: {}", target);
        } catch (Exception e) {
            log.warn("Failed to persist manus summary markdown", e);
        }
    }
}
