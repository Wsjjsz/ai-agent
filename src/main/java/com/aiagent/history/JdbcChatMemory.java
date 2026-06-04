package com.aiagent.history;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JdbcChatMemory implements ChatMemory {

    private final ChatHistoryRepository chatHistoryRepository;

    public JdbcChatMemory(ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        ConversationScope scope = parseConversationScope(conversationId);
        if (scope == null) {
            return;
        }
        for (Message message : messages) {
            String role = "user";
            if (message instanceof SystemMessage) {
                role = "system";
            } else if (message instanceof AssistantMessage) {
                role = "assistant";
            } else if (message instanceof ToolResponseMessage) {
                role = "tool";
            } else if (message instanceof UserMessage) {
                role = "user";
            }
            chatHistoryRepository.addMessage(scope.sessionId(), scope.userId(), role, ((org.springframework.ai.chat.messages.AbstractMessage) message).getText());
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        ConversationScope scope = parseConversationScope(conversationId);
        if (scope == null) {
            return List.of();
        }
        List<Map<String, Object>> records = chatHistoryRepository.listMessages(scope.sessionId(), scope.userId());
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String role = (String) record.get("role");
            String content = (String) record.get("content");
            if ("system".equals(role)) {
                messages.add(new SystemMessage(content));
            } else if ("assistant".equals(role)) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        ConversationScope scope = parseConversationScope(conversationId);
        if (scope != null) {
            chatHistoryRepository.deleteSession(scope.sessionId(), scope.userId());
        }
    }

    private ConversationScope parseConversationScope(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String[] parts = conversationId.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        try {
            return new ConversationScope(Long.parseLong(parts[0]), parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ConversationScope(long userId, String sessionId) {
    }
}
