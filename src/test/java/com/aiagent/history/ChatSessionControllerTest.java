package com.aiagent.history;

import com.aiagent.config.GlobalExceptionHandler;
import com.aiagent.auth.AuthContext;
import com.aiagent.auth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSessionControllerTest {

    private RecordingChatHistoryRepository chatHistoryRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatHistoryRepository = new RecordingChatHistoryRepository();
        ChatSessionController controller = new ChatSessionController(chatHistoryRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSessionUsesDefaultsWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/session/create")
                        .requestAttr(AuthContext.CURRENT_USER_ATTRIBUTE, testUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("新对话"))
                .andExpect(jsonPath("$.mode").value("basic"));

        assertEquals("新对话", chatHistoryRepository.createdTitle);
        assertEquals("basic", chatHistoryRepository.createdMode);
        assertEquals(1L, chatHistoryRepository.createdUserId);
    }

    @Test
    void deleteSessionDelegatesToRepository() throws Exception {
        mockMvc.perform(delete("/session/session-1")
                        .requestAttr(AuthContext.CURRENT_USER_ATTRIBUTE, testUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals("session-1", chatHistoryRepository.deletedId);
    }

    @Test
    void renameSessionRejectsBlankTitle() throws Exception {
        mockMvc.perform(put("/session/session-1/rename")
                        .requestAttr(AuthContext.CURRENT_USER_ATTRIBUTE, testUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        assertNull(chatHistoryRepository.renamedId);
    }

    private static class RecordingChatHistoryRepository extends ChatHistoryRepository {

        private String createdTitle;
        private String createdMode;
        private long createdUserId;
        private String deletedId;
        private String renamedId;

        RecordingChatHistoryRepository() {
            super(null);
        }

        @Override
        public void createSession(String id, long userId, String title, String mode) {
            this.createdTitle = title;
            this.createdMode = mode;
            this.createdUserId = userId;
        }

        @Override
        public void deleteSession(String id, long userId) {
            this.deletedId = id;
        }

        @Override
        public void renameSession(String id, long userId, String title) {
            this.renamedId = id;
        }

        @Override
        public List<Map<String, Object>> listSessions(long userId) {
            return List.of();
        }

        @Override
        public boolean ownsSession(String id, long userId) {
            return true;
        }
    }

    private AuthenticatedUser testUser() {
        return new AuthenticatedUser(1L, "tester", "Tester", "");
    }
}
