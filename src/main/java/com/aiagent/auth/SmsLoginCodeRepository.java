package com.aiagent.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SmsLoginCodeRepository {

    private final JdbcTemplate jdbcTemplate;

    public SmsLoginCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LocalDateTime> findLatestCreateTime(String phone, String scene) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT create_time FROM sms_login_code WHERE phone = ? AND scene = ? ORDER BY create_time DESC LIMIT 1",
                phone,
                scene
        );
        return rows.stream()
                .findFirst()
                .map(row -> toLocalDateTime(row.get("create_time")));
    }

    public int countCreatedSince(String phone, String scene, LocalDateTime since) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sms_login_code WHERE phone = ? AND scene = ? AND create_time >= ?",
                Integer.class,
                phone,
                scene,
                since
        );
        return count == null ? 0 : count;
    }

    public int countByIpSince(String ipAddress, LocalDateTime since) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sms_login_code WHERE ip_address = ? AND create_time >= ?",
                Integer.class,
                ipAddress,
                since
        );
        return count == null ? 0 : count;
    }

    public int countByDeviceSince(String deviceId, LocalDateTime since) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sms_login_code WHERE device_id = ? AND create_time >= ?",
                Integer.class,
                deviceId,
                since
        );
        return count == null ? 0 : count;
    }

    public void create(String phone, String codeHash, String scene, LocalDateTime expireTime,
                       String ipAddress, String deviceId) {
        jdbcTemplate.update(
                "INSERT INTO sms_login_code (phone, code_hash, scene, expire_time, ip_address, device_id) VALUES (?, ?, ?, ?, ?, ?)",
                phone,
                codeHash,
                scene,
                expireTime,
                blankToNull(ipAddress),
                blankToNull(deviceId)
        );
    }

    public Optional<SmsCodeRecord> findLatestValidCode(String phone, String scene, LocalDateTime now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, phone, code_hash, scene, expire_time, consumed, attempt_count, create_time
                FROM sms_login_code
                WHERE phone = ? AND scene = ? AND consumed = FALSE AND expire_time > ?
                ORDER BY create_time DESC
                LIMIT 1
                """,
                phone,
                scene,
                now
        );
        return rows.stream().findFirst().map(this::toRecord);
    }

    public void increaseAttempt(long id) {
        jdbcTemplate.update(
                "UPDATE sms_login_code SET attempt_count = attempt_count + 1 WHERE id = ?",
                id
        );
    }

    public boolean consume(long id) {
        int updated = jdbcTemplate.update(
                "UPDATE sms_login_code SET consumed = TRUE WHERE id = ? AND consumed = FALSE",
                id
        );
        return updated > 0;
    }

    private SmsCodeRecord toRecord(Map<String, Object> row) {
        return new SmsCodeRecord(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("phone")),
                String.valueOf(row.get("code_hash")),
                String.valueOf(row.get("scene")),
                toLocalDateTime(row.get("expire_time")),
                Boolean.TRUE.equals(row.get("consumed")),
                ((Number) row.get("attempt_count")).intValue(),
                toLocalDateTime(row.get("create_time"))
        );
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SmsCodeRecord(
            long id,
            String phone,
            String codeHash,
            String scene,
            LocalDateTime expireTime,
            boolean consumed,
            int attemptCount,
            LocalDateTime createTime
    ) {
    }
}
