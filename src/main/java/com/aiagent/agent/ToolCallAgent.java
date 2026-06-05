package com.aiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                // 模型未调用工具 = 已完成推理，将其回复作为最终答案
                if (StrUtil.isNotBlank(assistantMessage.getText())) {
                    setState(AgentState.FINISHED);
                }
                return false;
            } else {
                emitAgentEvent("tool_plan", "计划调用工具", "running", toolCallInfo);
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());

            // 收集异常链中所有可用信息
            StringBuilder errInfo = new StringBuilder();
            if (e.getMessage() != null) errInfo.append(e.getMessage()).append(" ");
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) errInfo.append(cause.getMessage()).append(" ");
                cause = cause.getCause();
            }
            // 提取 RestClient 响应体（含 API 返回的 quota 错误码）
            if (e instanceof RestClientResponseException rre) {
                try {
                    String body = rre.getResponseBodyAsString();
                    if (body != null) errInfo.append(body).append(" ");
                } catch (Exception ignored) {}
            }

            String errMsg = errInfo.toString();
            if (errMsg.contains("AllocationQuota") || errMsg.contains("FreeTierOnly")
                    || errMsg.contains("free tier") || errMsg.contains("exhausted")
                    || errMsg.contains("quota") || errMsg.contains("额度")) {
                getMessageList().add(new AssistantMessage("额度已用完，请等待充值～"));
                setState(AgentState.FINISHED);
            } else {
                getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            }
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        String toolNames = toolCallChatResponse.getResult().getOutput().getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .collect(Collectors.joining("、"));
        emitAgentEvent("tool_start", "开始执行工具", "running", StrUtil.blankToDefault(toolNames, "工具调用"));
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = toolExecutionResult.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .reduce((previous, current) -> current)
                .orElse(null);
        if (toolResponseMessage == null) {
            log.warn("Tool execution completed, but no ToolResponseMessage was found in conversation history. Last message type: {}",
                    CollUtil.getLast(toolExecutionResult.conversationHistory()) == null
                            ? "null"
                            : CollUtil.getLast(toolExecutionResult.conversationHistory()).getClass().getName());
            emitAgentEvent("tool_result", "工具执行完成", "success", "工具执行完成，但未返回标准工具响应消息。");
            return "工具执行完成，但未返回标准工具响应消息。";
        }
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 在终止前补一段模型总结，避免最终结果只剩工具返回信息
            appendFinalSummaryMessage();
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        emitAgentEvent("tool_result", "工具执行完成", "success", results);
        return results;
    }

    private void appendFinalSummaryMessage() {
        try {
            List<Message> history = getMessageList();
            history.add(new UserMessage("""
                    请基于当前已有信息，直接输出最终总结（Markdown）：
                    - 仅保留：问题概述、关键信息、建议结论
                    - 不要输出工具调用过程、日志、HTML 标签源码
                    - 如果推理过程中已经搜索到相关图片，可以说明“相关图片已嵌入报告文件，推理过程也可查看图片素材”
                    - 不要输出远程图片 URL；图片应在报告文件中展示，推理过程可展示图片素材
                    - 如果已经生成报告文件，只说明报告已生成，不要逐条列出文件路径；文件入口由前端卡片展示
                    - 如果已经生成报告文件，最终总结要与报告核心内容一致；报告文件应承载更完整的推理内容和扩展分析
                    - 不要再调用任何工具
                    """));
            Prompt prompt = new Prompt(history, this.chatOptions);
            ChatResponse summaryResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .call()
                    .chatResponse();
            AssistantMessage summaryMessage = summaryResponse.getResult().getOutput();
            if (summaryMessage != null && StrUtil.isNotBlank(summaryMessage.getText())) {
                history.add(summaryMessage);
                setMessageList(history);
            }
        } catch (Exception e) {
            log.warn("Failed to append final summary message before termination: {}", e.getMessage());
        }
    }
}
