package com.aiagent.history;

import com.aiagent.history.dto.AddMessageRequest;
import com.aiagent.history.dto.CreateSessionRequest;
import com.aiagent.history.dto.PinSessionRequest;
import com.aiagent.history.dto.RenameSessionRequest;
import com.aiagent.auth.AuthContext;
import com.aiagent.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/session")
public class ChatSessionController {

    private final ChatHistoryRepository chatHistoryRepository;

    public ChatSessionController(ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @GetMapping("/list")
    public List<Map<String, Object>> listSessions(HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        return chatHistoryRepository.listSessions(user.id());
    }

    @PostMapping("/create")
    public Map<String, Object> createSession(HttpServletRequest servletRequest, @Valid @RequestBody CreateSessionRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        String title = defaultIfBlank(request.title(), "新对话");
        String mode = defaultIfBlank(request.mode(), "basic");
        String id = UUID.randomUUID().toString();
        chatHistoryRepository.createSession(id, user.id(), title, mode);
        return Map.of("id", id, "title", title, "mode", mode);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteSession(HttpServletRequest servletRequest, @PathVariable String id) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        requireOwnedSession(id, user.id());
        chatHistoryRepository.deleteSession(id, user.id());
        return Map.of("success", true);
    }

    @PutMapping("/{id}/rename")
    public Map<String, Object> renameSession(HttpServletRequest servletRequest, @PathVariable String id, @Valid @RequestBody RenameSessionRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        requireOwnedSession(id, user.id());
        String title = request.title().trim();
        chatHistoryRepository.renameSession(id, user.id(), title);
        return Map.of("success", true);
    }

    @PutMapping("/{id}/pin")
    public Map<String, Object> pinSession(HttpServletRequest servletRequest, @PathVariable String id, @Valid @RequestBody PinSessionRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        requireOwnedSession(id, user.id());
        chatHistoryRepository.pinSession(id, user.id(), request.pinned());
        return Map.of("success", true);
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> listMessages(HttpServletRequest servletRequest, @PathVariable String id) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        requireOwnedSession(id, user.id());
        return chatHistoryRepository.listMessages(id, user.id());
    }

    @PostMapping("/{id}/messages")
    public Map<String, Object> addMessage(HttpServletRequest servletRequest, @PathVariable String id, @Valid @RequestBody AddMessageRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(servletRequest);
        requireOwnedSession(id, user.id());
        String role = defaultIfBlank(request.role(), "user");
        String content = request.content().trim();
        chatHistoryRepository.addMessage(id, user.id(), role, content);
        return Map.of("success", true);
    }

    private void requireOwnedSession(String sessionId, long userId) {
        if (!chatHistoryRepository.ownsSession(sessionId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
