CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(500),
    phone VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id),
    title VARCHAR(255) NOT NULL,
    mode VARCHAR(64),
    pinned BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message_record (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(64),
    content TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELETE FROM chat_message_record
WHERE NOT EXISTS (
    SELECT 1 FROM chat_session WHERE chat_session.id = chat_message_record.session_id
);

ALTER TABLE chat_message_record DROP CONSTRAINT IF EXISTS fk_chat_message_session;
ALTER TABLE chat_message_record
    ADD CONSTRAINT fk_chat_message_session
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE;

-- 兼容已有表：添加 pinned 字段
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS pinned BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_user(id);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32);

CREATE TABLE IF NOT EXISTS sms_login_code (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(32) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    expire_time TIMESTAMP NOT NULL,
    consumed BOOLEAN DEFAULT FALSE,
    attempt_count INTEGER DEFAULT 0,
    ip_address VARCHAR(64),
    device_id VARCHAR(128),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS guest_usage_quota (
    quota_key VARCHAR(160) PRIMARY KEY,
    usage_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_index_manifest (
    source_path VARCHAR(1000) PRIMARY KEY,
    file_hash VARCHAR(64) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    embedding_model VARCHAR(128) NOT NULL,
    splitter_version VARCHAR(64) NOT NULL,
    indexed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_phone ON app_user(phone) WHERE phone IS NOT NULL;
ALTER TABLE sms_login_code ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);
ALTER TABLE sms_login_code ADD COLUMN IF NOT EXISTS device_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_time ON chat_session(user_id, pinned DESC, update_time DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_session_time ON chat_message_record(session_id, create_time ASC);
CREATE INDEX IF NOT EXISTS idx_sms_login_code_phone_scene_time ON sms_login_code(phone, scene, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sms_login_code_ip_time ON sms_login_code(ip_address, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sms_login_code_device_time ON sms_login_code(device_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_rag_index_manifest_update_time ON rag_index_manifest(update_time DESC);
