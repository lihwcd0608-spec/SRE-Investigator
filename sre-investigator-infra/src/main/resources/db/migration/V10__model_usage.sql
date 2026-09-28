CREATE TABLE investigator_model_usage (
    id BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    user_id BINARY(16) NOT NULL,
    username VARCHAR(80) NOT NULL,
    role_codes VARCHAR(400) NOT NULL,
    project_id BINARY(16) NULL,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(120) NOT NULL,
    call_count INT NOT NULL DEFAULT 1,
    prompt_tokens BIGINT NULL,
    completion_tokens BIGINT NULL,
    total_tokens BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_usage_occurred (occurred_at),
    KEY idx_usage_user_occurred (user_id, occurred_at),
    KEY idx_usage_model_occurred (provider, model, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000008','-','')), 'menu.usage', '用量', 'MENU', '/settings/usage', 80);

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code = 'menu.usage';
