CREATE TABLE IF NOT EXISTS investigator_feishu_chat_binding (
    id                      CHAR(36)     NOT NULL PRIMARY KEY,
    project_id              CHAR(36)     NOT NULL,
    chat_id                 VARCHAR(128) NOT NULL,
    chat_name               VARCHAR(255) NOT NULL DEFAULT '',
    environment             VARCHAR(16)  NOT NULL,
    default_route           VARCHAR(64)  NOT NULL DEFAULT '',
    default_repository_id   CHAR(36)     NULL,
    last_conversation_id    CHAR(36)     NULL,
    enabled                 TINYINT(1)   NOT NULL DEFAULT 1,
    -- 用 DATETIME 而非 TIMESTAMP：某些 MySQL（explicit_defaults_for_timestamp=OFF + 严格 sql_mode）
    -- 下第二个 TIMESTAMP NOT NULL 会被塞 '0000-00-00' 默认值而报 1067。V19 会重建为 DATETIME(6)。
    created_at              DATETIME(3)  NOT NULL,
    updated_at              DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_feishu_chat_id (chat_id),
    KEY idx_feishu_chat_project (project_id)
);
