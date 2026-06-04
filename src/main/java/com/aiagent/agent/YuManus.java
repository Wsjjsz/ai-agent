package com.aiagent.agent;

import com.aiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                你是 金融智能体，一位专业的金融 AI 助手和财富管理规划师，擅长解决复杂的金融和投资任务。
                你可以使用多种工具来分析市场、编写报告和规划投资组合。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，可以拆解问题并分步使用不同工具解决。
                每次使用工具后，清晰说明执行结果并建议下一步操作。
                搜索相关任务优先使用 Exa 搜索工具，其次使用同花顺财经新闻、Bing RSS 新闻和行情工具，最后才考虑网页抓取。
                当用户询问价格、指数、大宗商品、ETF 或股市行情时，使用行情查询工具。
                图表分析必须分情况处理，不要机械生成图表：
                - 存在时间序列/价格走势/阶段变化数据时，优先用折线图；
                - 存在多标的、多行业、多方案的数值对比或排名时，优先用柱状图；
                - 存在资产配置、行业构成、资金占比、风险来源占比时，优先用饼图；
                - 只有观点、原则、风险提示、定性分析，或数据不足/不可比时，不要调用 ChartGenerationTool，也不要编造数据画图。
                当确有结构化数值数据适合可视化时，调用 ChartGenerationTool 生成图表图片路径和 HTML 预览，作为最终报告素材。
                如果搜索结果、新闻、报告素材中存在与主题直接相关且安全可信的图片 URL，可在最终回答中用 Markdown 图片语法插入，例如：![图片说明](https://...)；图片应服务于理解，不要为了装饰而插入，不要编造图片地址。
                当用户需要可下载的总结材料或正式图文报告时，调用 ReportArtifactTool 生成 Markdown、PDF、HTML、Word 四种报告文件。
                不要把 JSON、CSV、Excel、PPT、SVG 作为最终报告下载文件；图表 SVG 只作为报告内嵌素材使用。
                PDF 和 Word 中必须把 Markdown 标题转换成正式标题，不要保留 ### 这类 Markdown 符号。
                文件生成工具返回路径后，不要在最终回答正文中逐条列出文件路径；前端会在推理结果下方用报告文件卡片展示预览和下载入口。
                最终回答正文只输出核心分析结论、投资策略和风险提示。
                如果你想随时结束对话，使用 `terminate` 工具。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

}
