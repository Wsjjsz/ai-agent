package com.aiagent.controller;

import com.aiagent.app.FinanceApp;
import com.aiagent.app.FinanceAppService;
import com.aiagent.auth.AuthContext;
import com.aiagent.auth.AuthenticatedUser;
import com.aiagent.auth.GuestQuotaService;
import com.aiagent.history.ChatHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiControllerUnitTest {

    @Test
    void financeSseReturnsModelChunks() {
        FakeFinanceAppService financeApp = new FakeFinanceAppService();
        AiController aiController = new AiController();
        ReflectionTestUtils.setField(aiController, "financeApp", financeApp);
        ReflectionTestUtils.setField(aiController, "chatHistoryRepository", new FakeChatHistoryRepository());
        ReflectionTestUtils.setField(aiController, "guestQuotaService", new GuestQuotaService(null));

        List<String> chunks = aiController
                .doChatWithFinanceAppSSE(request(), Map.of("message", "你好", "chatId", "chat-1"))
                .collectList()
                .block();

        assertEquals(List.of("你", "好"), chunks);
        assertEquals("你好", financeApp.lastStreamMessage);
        assertEquals("chat-1", financeApp.lastStreamChatId);
    }

    @Test
    void ragEndpointReturnsAnswerWithReferences() {
        FakeFinanceAppService financeApp = new FakeFinanceAppService();
        AiController aiController = new AiController();
        ReflectionTestUtils.setField(aiController, "financeApp", financeApp);
        ReflectionTestUtils.setField(aiController, "chatHistoryRepository", new FakeChatHistoryRepository());
        ReflectionTestUtils.setField(aiController, "guestQuotaService", new GuestQuotaService(null));

        String result = aiController.doChatWithRag(request(), Map.of("message", "如何做资产配置", "chatId", "chat-1"));

        assertTrue(result.contains("参考来源"));
        assertEquals("如何做资产配置", financeApp.lastRagMessage);
        assertEquals("chat-1", financeApp.lastRagChatId);
    }

    private static class FakeFinanceAppService implements FinanceAppService {

        private String lastStreamMessage;
        private String lastStreamChatId;
        private String lastRagMessage;
        private String lastRagChatId;

        @Override
        public String doChat(String message, String chatId, long userId) {
            return "ok";
        }

        @Override
        public Flux<String> doChatByStream(String message, String chatId, long userId) {
            this.lastStreamMessage = message;
            this.lastStreamChatId = chatId;
            return Flux.just("你", "好");
        }

        @Override
        public FinanceApp.FinanceReport doChatWithReport(String message, String chatId, long userId) {
            return new FinanceApp.FinanceReport("报告", List.of("建议"));
        }

        @Override
        public String doChatWithRag(String message, String chatId, long userId) {
            this.lastRagMessage = message;
            this.lastRagChatId = chatId;
            return "建议分散配置。\n\n---\n参考来源：\n- [来源1] 资产配置.md";
        }

        @Override
        public String doChatWithTools(String message, String chatId, long userId) {
            return "工具调用失败，已降级处理";
        }

        @Override
        public String doChatWithMcp(String message, String chatId, long userId) {
            return "mcp";
        }
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.CURRENT_USER_ATTRIBUTE, new AuthenticatedUser(1L, "tester", "Tester", ""));
        return request;
    }

    private static class FakeChatHistoryRepository extends ChatHistoryRepository {

        FakeChatHistoryRepository() {
            super(null);
        }

        @Override
        public boolean ownsSession(String id, long userId) {
            return true;
        }
    }
}
