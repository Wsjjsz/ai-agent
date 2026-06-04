package com.aiagent.app;

import reactor.core.publisher.Flux;

public interface FinanceAppService {

    String doChat(String message, String chatId, long userId);

    Flux<String> doChatByStream(String message, String chatId, long userId);

    FinanceApp.FinanceReport doChatWithReport(String message, String chatId, long userId);

    String doChatWithRag(String message, String chatId, long userId);

    String doChatWithTools(String message, String chatId, long userId);

    String doChatWithMcp(String message, String chatId, long userId);
}
