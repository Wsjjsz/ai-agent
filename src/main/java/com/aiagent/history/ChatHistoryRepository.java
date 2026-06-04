package com.aiagent.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ChatHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public ChatHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listSessions(long userId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM chat_session WHERE user_id = ? ORDER BY pinned DESC, update_time DESC",
                userId
        );
    }

    public void createSession(String id, long userId, String title, String mode) {
        jdbcTemplate.update(
                "INSERT INTO chat_session (id, user_id, title, mode) VALUES (?, ?, ?, ?)",
                id,
                userId,
                title,
                mode
        );
    }

    public boolean ownsSession(String id, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_session WHERE id = ? AND user_id = ?",
                Integer.class,
                id,
                userId
        );
        return count != null && count > 0;
    }

    public void deleteSession(String id, long userId) {
        jdbcTemplate.update(
                "DELETE FROM chat_message_record WHERE session_id IN (SELECT id FROM chat_session WHERE id = ? AND user_id = ?)",
                id,
                userId
        );
        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ? AND user_id = ?", id, userId);
    }

    public void renameSession(String id, long userId, String title) {
        jdbcTemplate.update(
                "UPDATE chat_session SET title = ?, update_time = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                title,
                id,
                userId
        );
    }

    public void pinSession(String id, long userId, boolean pinned) {
        if (pinned) {
            jdbcTemplate.update(
                    "UPDATE chat_session SET pinned = TRUE, update_time = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                    id,
                    userId
            );
        } else {
            jdbcTemplate.update("UPDATE chat_session SET pinned = FALSE WHERE id = ? AND user_id = ?", id, userId);
        }
    }

    public List<Map<String, Object>> listMessages(String sessionId, long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT m.*
                FROM chat_message_record m
                JOIN chat_session s ON s.id = m.session_id
                WHERE m.session_id = ? AND s.user_id = ?
                ORDER BY m.create_time ASC
                """,
                sessionId,
                userId
        );
    }

    public void addMessage(String sessionId, long userId, String role, String content) {
        if (!ownsSession(sessionId, userId)) {
            return;
        }
        jdbcTemplate.update("INSERT INTO chat_message_record (session_id, role, content) VALUES (?, ?, ?)", sessionId, role, content);
        jdbcTemplate.update("UPDATE chat_session SET update_time = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?", sessionId, userId);
    }

    public void transferSessions(long fromUserId, long toUserId) {
        if (fromUserId == toUserId) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE chat_session SET user_id = ?, update_time = CURRENT_TIMESTAMP WHERE user_id = ?",
                toUserId,
                fromUserId
        );
    }
}
