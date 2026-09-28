-- 飞书群按「发送人」隔离会话：每个 chat_id + sender_id 各自一条会话线程，避免多人同群串台。
-- last_active_at 供闲置超时判断；超时或换人自动新开会话。
CREATE TABLE investigator_feishu_thread (
    id              BINARY(16)   NOT NULL,
    chat_id         VARCHAR(128) NOT NULL,
    sender_id       VARCHAR(128) NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    last_active_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feishu_thread (chat_id, sender_id),
    KEY idx_feishu_thread_active (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
