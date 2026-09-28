CREATE TABLE investigator_automation_job (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    schedule_kind VARCHAR(20) NOT NULL,
    schedule_expr VARCHAR(80) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    payload_kind VARCHAR(20) NOT NULL,
    session_target VARCHAR(20) NOT NULL DEFAULT 'ISOLATED',
    prompt LONGTEXT NULL,
    route VARCHAR(80) NULL,
    conversation_id BINARY(16) NULL,
    delete_after_run BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_seconds INT NOT NULL DEFAULT 600,
    last_run_at DATETIME(6) NULL,
    next_run_at DATETIME(6) NULL,
    last_status VARCHAR(30) NULL,
    consecutive_errors INT NOT NULL DEFAULT 0,
    running_execution_id BINARY(16) NULL,
    running_since DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_automation_job_project (project_id, enabled),
    KEY idx_automation_job_due (enabled, next_run_at),
    CONSTRAINT fk_automation_job_project FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_automation_execution (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    run_id BINARY(16) NULL,
    conversation_id BINARY(16) NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    summary VARCHAR(400) NULL,
    error_message TEXT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_automation_exec_job (job_id, started_at),
    CONSTRAINT fk_automation_exec_job FOREIGN KEY (job_id) REFERENCES investigator_automation_job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000009','-','')), 'menu.automations', '定时', 'MENU', '/automations', 35);

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code = 'menu.automations';

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'OPERATOR'
  AND p.code = 'menu.automations';
