package com.aiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@Disabled("Requires database, model provider, and external tools; run manually as an integration test.")
@SpringBootTest
class FinanceAppTest {

    @Resource
    private FinanceApp financeApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我想了解一下家庭资产配置";
        String answer = financeApp.doChat(message, chatId, 1L);
        // 第二轮
        message = "我目前股票和基金占比偏高，想增加一些稳健资产";
        answer = financeApp.doChat(message, chatId, 1L);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "刚才提到我哪个资产占比偏高？帮我回忆一下";
        answer = financeApp.doChat(message, chatId, 1L);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "请帮我生成一份稳健型家庭资产配置建议，包含现金、债券、基金和黄金";
        FinanceApp.FinanceReport financeReport = financeApp.doChatWithReport(message, chatId, 1L);
        Assertions.assertNotNull(financeReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我有 20 万闲置资金，未来一年可能要买车，应该如何规划？";
        String answer = financeApp.doChatWithRag(message, chatId, 1L);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("帮我搜索最近黄金市场的主要驱动因素，并总结投资机会");

        // 测试网页抓取：财经信息分析
        testMessage("抓取一篇公开财经新闻并提炼对债券基金的影响");

        // 测试资源下载：图片下载
        testMessage("下载一张公开可访问的财经图表图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的风险偏好档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘家庭年度理财计划’PDF，包含预算、配置比例和风险提示");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = financeApp.doChatWithTools(message, chatId, 1L);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
//        String message = "我关注上海本地消费板块，请帮我检索近期相关新闻";
//        String answer =  financeApp.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);
        // 测试图片搜索 MCP
        String message = "帮我搜索一些黄金价格走势图片";
        String answer =  financeApp.doChatWithMcp(message, chatId, 1L);
        Assertions.assertNotNull(answer);
    }
}
