package com.aiagent.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> blockedUsernames;
    private final ApihzRandomAvatarService randomAvatarService;

    public UserRepository(JdbcTemplate jdbcTemplate,
                          @Value("${app.auth.blocked-usernames:demo,analyst,admin}") String blockedUsernames,
                          ApihzRandomAvatarService randomAvatarService) {
        this.jdbcTemplate = jdbcTemplate;
        this.randomAvatarService = randomAvatarService;
        this.blockedUsernames = Arrays.stream(blockedUsernames.split(","))
                .map(this::normalizeUsername)
                .filter(username -> !username.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<UserRecord> findByUsername(String username) {
        if (isBlockedUsername(username)) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM app_user WHERE username = ?",
                normalizeUsername(username)
        );
        return rows.stream().findFirst().map(this::toRecord);
    }

    public Optional<AuthenticatedUser> findAuthenticatedById(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, nickname, avatar_url FROM app_user WHERE id = ?",
                id
        );
        return rows.stream()
                .filter(row -> !isBlockedUsername(String.valueOf(row.get("username"))))
                .findFirst()
                .map(this::toAuthenticatedUser);
    }

    public Optional<AuthenticatedUser> findByPhone(String phone) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, nickname, avatar_url FROM app_user WHERE phone = ?",
                phone
        );
        return rows.stream()
                .filter(row -> !isBlockedUsername(String.valueOf(row.get("username"))))
                .findFirst()
                .map(this::toAuthenticatedUser);
    }

    public Optional<UserRecord> findLoginRecordByPhone(String phone) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM app_user WHERE phone = ?",
                phone
        );
        return rows.stream()
                .filter(row -> !isBlockedUsername(String.valueOf(row.get("username"))))
                .findFirst()
                .map(this::toRecord);
    }

    public AuthenticatedUser findOrCreateGuest(String guestId) {
        String safeGuestId = normalizeGuestId(guestId);
        String username = "guest_" + safeGuestId;
        Optional<UserRecord> existing = findByUsername(username);
        if (existing.isPresent()) {
            UserRecord record = existing.get();
            return new AuthenticatedUser(record.id(), record.username(), record.nickname(), record.avatarUrl());
        }
        try {
            return createUser(username, "guest", "访客", null);
        } catch (Exception ignored) {
            UserRecord record = findByUsername(username).orElseThrow();
            return new AuthenticatedUser(record.id(), record.username(), record.nickname(), record.avatarUrl());
        }
    }

    public AuthenticatedUser findOrCreatePhoneUser(String phone) {
        Optional<AuthenticatedUser> existing = findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }

        String digits = phone.replaceAll("\\D", "");
        String nickname = "手机用户" + digits.substring(Math.max(0, digits.length() - 4));
        String usernameBase = "phone_" + digits;
        String avatarUrl = randomAvatarService.randomAvatarUrl().orElse("");
        for (int attempt = 0; attempt < 3; attempt++) {
            String username = attempt == 0
                    ? usernameBase
                    : usernameBase + "_" + (System.currentTimeMillis() % 100000) + "_" + attempt;
            try {
                Long id = jdbcTemplate.queryForObject(
                        "INSERT INTO app_user (username, password_hash, nickname, avatar_url, phone) VALUES (?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        normalizeUsername(username),
                        "sms_login_only",
                        nickname,
                        blankToNull(avatarUrl),
                        phone
                );
                return new AuthenticatedUser(id, normalizeUsername(username), nickname, avatarUrl);
            } catch (DuplicateKeyException ignored) {
                existing = findByPhone(phone);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
        }
        return findByPhone(phone).orElseThrow(() -> new IllegalStateException("手机号账号创建失败"));
    }

    public AuthenticatedUser createUser(String username, String passwordHash, String nickname, String avatarUrl) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash, nickname, avatar_url) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                normalizeUsername(username),
                passwordHash,
                nickname,
                blankToNull(avatarUrl)
        );
        return new AuthenticatedUser(id, normalizeUsername(username), nickname, blankToNull(avatarUrl));
    }

    public AuthenticatedUser updateProfile(long id, String nickname, String avatarUrl) {
        jdbcTemplate.update(
                "UPDATE app_user SET nickname = ?, avatar_url = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                nickname,
                blankToNull(avatarUrl),
                id
        );
        return findAuthenticatedById(id).orElseThrow();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE app_user SET password_hash = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                passwordHash,
                id
        );
    }

    private UserRecord toRecord(Map<String, Object> row) {
        return new UserRecord(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("username")),
                String.valueOf(row.get("password_hash")),
                String.valueOf(row.get("nickname")),
                row.get("avatar_url") == null ? "" : String.valueOf(row.get("avatar_url"))
        );
    }

    private AuthenticatedUser toAuthenticatedUser(Map<String, Object> row) {
        return new AuthenticatedUser(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("username")),
                String.valueOf(row.get("nickname")),
                row.get("avatar_url") == null ? "" : String.valueOf(row.get("avatar_url"))
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private boolean isBlockedUsername(String username) {
        return blockedUsernames.contains(normalizeUsername(username));
    }

    private String normalizeGuestId(String guestId) {
        String value = guestId == null ? "" : guestId.trim().toLowerCase();
        value = value.replaceAll("[^a-z0-9_-]", "");
        if (value.length() < 8) {
            throw new IllegalArgumentException("invalid guest id");
        }
        return value.substring(0, Math.min(value.length(), 64));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UserRecord(
            long id,
            String username,
            String passwordHash,
            String nickname,
            String avatarUrl
    ) {
    }
}
