-- V18 误用 CHAR(36)，Hibernate UUID 校验要求 BINARY(16)。表刚加、无业务数据，直接重建。
DROP TABLE IF EXISTS investigator_feishu_chat_binding;

CREATE TABLE investigator_feishu_chat_binding (
    id                    BINARY(16)   NOT NULL,
    project_id            BINARY(16)   NOT NULL,
    chat_id               VARCHAR(128) NOT NULL,
    chat_name             VARCHAR(255) NOT NULL DEFAULT '',
    environment           VARCHAR(20)  NOT NULL,
    default_route         VARCHAR(64)  NOT NULL DEFAULT '',
    default_repository_id BINARY(16)   NULL,
    last_conversation_id  BINARY(16)   NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feishu_chat_id (chat_id),
    KEY idx_feishu_chat_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
