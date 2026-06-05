package com.aiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${search-api.enabled:false}")
    private boolean searchApiEnabled;

    @Value("${exa.api-key:${EXA_API_KEY:}}")
    private String exaApiKey;

    @Value("${apihz.id:${APIHZ_ID:}}")
    private String apihzId;

    @Value("${apihz.key:${APIHZ_KEY:}}")
    private String apihzKey;

    @Value("${apihz.image-search.endpoint:https://cn.apihz.cn/api/img/apihzimgbaidu.php}")
    private String apihzImageSearchEndpoint;

    @Value("${app.tools.terminal.enabled:false}")
    private boolean terminalToolEnabled;

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        ExaSearchTool exaSearchTool = new ExaSearchTool(exaApiKey);
        ApihzImageSearchTool apihzImageSearchTool = new ApihzImageSearchTool(
                apihzId,
                apihzKey,
                apihzImageSearchEndpoint
        );
        FinanceNewsSearchTool financeNewsSearchTool = new FinanceNewsSearchTool();
        NewsRssSearchTool newsRssSearchTool = new NewsRssSearchTool();
        MarketQuoteTool marketQuoteTool = new MarketQuoteTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        ChartGenerationTool chartGenerationTool = new ChartGenerationTool();
        ReportArtifactTool reportArtifactTool = new ReportArtifactTool();
        TerminateTool terminateTool = new TerminateTool();

        List<Object> tools = new ArrayList<>();
        tools.add(fileOperationTool);
        tools.add(exaSearchTool);
        tools.add(apihzImageSearchTool);
        if (searchApiEnabled) {
            tools.add(new WebSearchTool(searchApiKey));
        }
        tools.add(financeNewsSearchTool);
        tools.add(newsRssSearchTool);
        tools.add(marketQuoteTool);
        tools.add(webScrapingTool);
        tools.add(resourceDownloadTool);
        if (terminalToolEnabled) {
            tools.add(new TerminalOperationTool());
        }
        tools.add(chartGenerationTool);
        tools.add(reportArtifactTool);
        tools.add(terminateTool);

        return ToolCallbacks.from(tools.toArray());
    }
}
