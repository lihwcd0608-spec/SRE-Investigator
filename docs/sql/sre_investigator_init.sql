-- SRE Investigator 控制面全量建库（对齐 Flyway V1～V25 终态）
-- 适用：空库 / 新环境一次性导入。已有库不要跑本文件，继续靠应用启动时的 Flyway。
-- 本机：mysql -h127.0.0.1 -P3306 -uroot -p123456 < docs/sql/sre_investigator_init.sql
-- 导入后启动应用即可；AuthBootstrap 会在无用户时创建 admin（local 默认 admin123456）。
-- 末尾写入 flyway_schema_history 到 25，启动时只会跑比 25 更新的迁移。

CREATE DATABASE IF NOT EXISTS sre_investigator
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE sre_investigator;

CREATE TABLE investigator_project (
    id BINARY(16) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    topology VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_project_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_repository (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    repository_role VARCHAR(80) NOT NULL,
    root_path VARCHAR(1024) NOT NULL,
    git_url VARCHAR(1024) NULL,
    git_ref VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_repository_project_name (project_id, name),
    CONSTRAINT fk_investigator_repository_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_tool_binding (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    tool_type VARCHAR(40) NOT NULL,
    profile VARCHAR(80) NOT NULL,
    encrypted_configuration LONGTEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    summary VARCHAR(280) NULL,
    tags VARCHAR(400) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_binding (project_id, environment, tool_type, profile),
    CONSTRAINT fk_investigator_binding_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_run (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    conversation_mode VARCHAR(40) NOT NULL DEFAULT 'INVESTIGATE',
    question LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    report LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_run_project_created (project_id, created_at),
    CONSTRAINT fk_investigator_run_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_audit_event (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'OP',
    actor_user_id BINARY(16) NULL,
    actor_username VARCHAR(80) NULL,
    client_ip VARCHAR(64) NULL,
    success TINYINT(1) NULL,
    action VARCHAR(100) NOT NULL,
    target VARCHAR(512) NOT NULL,
    metadata LONGTEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_audit_run_time (run_id, occurred_at),
    KEY idx_investigator_audit_category_time (category, occurred_at),
    KEY idx_investigator_audit_actor_time (actor_user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_setting (
    setting_key VARCHAR(80) NOT NULL,
    encrypted_value LONGTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_conversation (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    conversation_mode VARCHAR(40) NOT NULL,
    origin VARCHAR(20) NOT NULL DEFAULT 'CONVERSATION',
    title VARCHAR(512) NULL,
    memory_summary LONGTEXT NULL,
    created_by_user_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_conversation_project_updated (project_id, updated_at),
    KEY idx_investigator_conversation_project_origin_updated (project_id, origin, updated_at),
    KEY idx_investigator_conversation_owner (project_id, created_by_user_id, updated_at),
    CONSTRAINT fk_investigator_conversation_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_message (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    message_role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    run_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_investigator_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_investigator_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES investigator_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_catalog_asset (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    route VARCHAR(80) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    origin VARCHAR(20) NOT NULL,
    catalog_id VARCHAR(80) NULL,
    package_name VARCHAR(200) NULL,
    source_url VARCHAR(1024) NULL,
    git_ref VARCHAR(120) NULL,
    command_line VARCHAR(200) NULL,
    args VARCHAR(512) NULL,
    content LONGTEXT NULL,
    summary VARCHAR(280) NULL,
    tags VARCHAR(400) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_catalog_asset (project_id, kind, slug),
    KEY idx_investigator_catalog_asset_project (project_id, kind, updated_at),
    CONSTRAINT fk_investigator_catalog_asset_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_catalog_asset_file (
    id BINARY(16) NOT NULL,
    asset_id BINARY(16) NOT NULL,
    path VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_catalog_asset_file (asset_id, path),
    KEY idx_investigator_catalog_asset_file_asset (asset_id, sort_order),
    CONSTRAINT fk_investigator_catalog_asset_file_asset
        FOREIGN KEY (asset_id) REFERENCES investigator_catalog_asset (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_user (
    id BINARY(16) NOT NULL,
    username VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(200) NULL,
    password_hash VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_user_identity (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_identity (provider, external_id),
    CONSTRAINT fk_investigator_identity_user
        FOREIGN KEY (user_id) REFERENCES investigator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_role (
    id BINARY(16) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(400) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_permission (
    id BINARY(16) NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    perm_type VARCHAR(20) NOT NULL,
    menu_path VARCHAR(200) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investigator_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_role_permission (
    role_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_investigator_rp_role FOREIGN KEY (role_id) REFERENCES investigator_role (id),
    CONSTRAINT fk_investigator_rp_perm FOREIGN KEY (permission_id) REFERENCES investigator_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_user_role (
    user_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_investigator_ur_user FOREIGN KEY (user_id) REFERENCES investigator_user (id),
    CONSTRAINT fk_investigator_ur_role FOREIGN KEY (role_id) REFERENCES investigator_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investigator_project_member (
    project_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_investigator_pm_project FOREIGN KEY (project_id) REFERENCES investigator_project (id),
    CONSTRAINT fk_investigator_pm_user FOREIGN KEY (user_id) REFERENCES investigator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE investigator_memory_fact (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    source_conversation_id BINARY(16) NULL,
    qdrant_point_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memory_fact_project_updated (project_id, updated_at),
    CONSTRAINT fk_memory_fact_project
        FOREIGN KEY (project_id) REFERENCES investigator_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE investigator_feishu_chat_binding (
    id                    BINARY(16)   NOT NULL,
    project_id            BINARY(16)   NOT NULL,
    chat_id               VARCHAR(128) NOT NULL,
    chat_name             VARCHAR(255) NOT NULL DEFAULT '',
    environment           VARCHAR(20)  NOT NULL,
    allowed_environments  VARCHAR(64)  NOT NULL DEFAULT '',
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

INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000001','-','')), 'menu.projects', '项目', 'MENU', '/projects', 10),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000002','-','')), 'menu.repositories', '代码仓', 'MENU', '/repositories', 20),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000003','-','')), 'menu.bindings', '工具', 'MENU', '/bindings', 30),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000011','-','')), 'menu.agents', '领域专家编排', 'MENU', '/agents', 32),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000009','-','')), 'menu.automations', '定时', 'MENU', '/automations', 35),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000004','-','')), 'menu.investigate', '对话', 'MENU', '/investigate', 40),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000010','-','')), 'menu.review', '代码审查', 'MENU', '/review', 45),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000005','-','')), 'menu.model', '模型', 'MENU', '/settings/model', 50),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000006','-','')), 'menu.users', '用户', 'MENU', '/settings/users', 60),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000007','-','')), 'menu.roles', '角色', 'MENU', '/settings/roles', 70),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000008','-','')), 'menu.usage', '用量', 'MENU', '/settings/usage', 80),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000012','-','')), 'menu.audit', '审计日志', 'MENU', '/settings/audit', 90),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000001','-','')), 'action.projects.write', '项目-创建/修改', 'ACTION', NULL, 110),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000002','-','')), 'action.projects.delete', '项目-删除', 'ACTION', NULL, 111),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000003','-','')), 'action.repositories.write', '代码仓-创建/修改', 'ACTION', NULL, 120),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000004','-','')), 'action.repositories.delete', '代码仓-删除', 'ACTION', NULL, 121),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000005','-','')), 'action.bindings.write', '工具-创建/修改', 'ACTION', NULL, 130),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000006','-','')), 'action.bindings.delete', '工具-删除', 'ACTION', NULL, 131),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000007','-','')), 'action.agents.write', '专家编排-创建/修改', 'ACTION', NULL, 140),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000008','-','')), 'action.agents.delete', '专家编排-删除', 'ACTION', NULL, 141),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000009','-','')), 'action.automations.write', '定时-创建/修改', 'ACTION', NULL, 150),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000010','-','')), 'action.automations.delete', '定时-删除', 'ACTION', NULL, 151),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000011','-','')), 'action.model.write', '模型-修改', 'ACTION', NULL, 160),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000012','-','')), 'action.users.write', '用户-管理', 'ACTION', NULL, 170),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000013','-','')), 'action.roles.write', '角色-管理', 'ACTION', NULL, 180),
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000014','-','')), 'action.feishu.write', '飞书群-配置', 'ACTION', NULL, 190);

INSERT INTO investigator_role (id, code, name, builtin, description) VALUES
    (UNHEX(REPLACE('b1000000-0000-0000-0000-000000000001','-','')), 'SUPER_ADMIN', '超级管理员', TRUE, '全部菜单与操作，可见全部项目'),
    (UNHEX(REPLACE('b1000000-0000-0000-0000-000000000002','-','')), 'OPERATOR', '运维', TRUE, '项目、代码仓、领域专家编排、定时、对话、审查；不可配置工具'),
    (UNHEX(REPLACE('b1000000-0000-0000-0000-000000000003','-','')), 'VIEWER', '只读', TRUE, '项目和对话');

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN';

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'OPERATOR'
  AND p.code IN (
      'menu.projects', 'menu.repositories', 'menu.agents', 'menu.automations',
      'menu.investigate', 'menu.review',
      'action.projects.write', 'action.projects.delete',
      'action.repositories.write', 'action.repositories.delete',
      'action.agents.write', 'action.agents.delete',
      'action.automations.write', 'action.automations.delete'
  );

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'VIEWER'
  AND p.code IN ('menu.projects', 'menu.investigate');

CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success TINYINT(1) NOT NULL,
    PRIMARY KEY (installed_rank),
    KEY flyway_schema_history_s_idx (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO flyway_schema_history
    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES
    (1,  '1',  'control plane schema',                 'SQL', 'V1__control_plane_schema.sql',                 NULL, 'init', 0, 1),
    (2,  '2',  'align hibernate long text columns',    'SQL', 'V2__align_hibernate_long_text_columns.sql',    NULL, 'init', 0, 1),
    (3,  '3',  'model settings',                       'SQL', 'V3__model_settings.sql',                       NULL, 'init', 0, 1),
    (4,  '4',  'conversation mode',                    'SQL', 'V4__conversation_mode.sql',                    NULL, 'init', 0, 1),
    (5,  '5',  'repository git',                       'SQL', 'V5__repository_git.sql',                       NULL, 'init', 0, 1),
    (6,  '6',  'conversation thread',                  'SQL', 'V6__conversation_thread.sql',                  NULL, 'init', 0, 1),
    (7,  '7',  'catalog asset',                        'SQL', 'V7__catalog_asset.sql',                        NULL, 'init', 0, 1),
    (8,  '8',  'tool annotations',                     'SQL', 'V8__tool_annotations.sql',                     NULL, 'init', 0, 1),
    (9,  '9',  'rbac auth',                            'SQL', 'V9__rbac_auth.sql',                            NULL, 'init', 0, 1),
    (10, '10', 'model usage',                          'SQL', 'V10__model_usage.sql',                         NULL, 'init', 0, 1),
    (11, '11', 'memory fact',                          'SQL', 'V11__memory_fact.sql',                         NULL, 'init', 0, 1),
    (12, '12', 'automation job',                       'SQL', 'V12__automation_job.sql',                      NULL, 'init', 0, 1),
    (13, '13', 'conversation origin',                  'SQL', 'V13__conversation_origin.sql',                 NULL, 'init', 0, 1),
    (14, '14', 'review menu',                          'SQL', 'V14__review_menu.sql',                         NULL, 'init', 0, 1),
    (15, '15', 'environment test pre prod',            'SQL', 'V15__environment_test_pre_prod.sql',           NULL, 'init', 0, 1),
    (16, '16', 'review menu rename',                   'SQL', 'V16__review_menu_rename.sql',                  NULL, 'init', 0, 1),
    (17, '17', 'catalog asset file',                   'SQL', 'V17__catalog_asset_file.sql',                  NULL, 'init', 0, 1),
    (18, '18', 'feishu chat binding',                  'SQL', 'V18__feishu_chat_binding.sql',                 NULL, 'init', 0, 1),
    (19, '19', 'feishu chat binding binary uuid',      'SQL', 'V19__feishu_chat_binding_binary_uuid.sql',     NULL, 'init', 0, 1),
    (20, '20', 'agent plaza menu',                     'SQL', 'V20__agent_plaza_menu.sql',                    NULL, 'init', 0, 1),
    (21, '21', 'feishu allowed environments',          'SQL', 'V21__feishu_allowed_environments.sql',         NULL, 'init', 0, 1),
    (22, '22', 'agent plaza menu rename',              'SQL', 'V22__agent_plaza_menu_rename.sql',             NULL, 'init', 0, 1),
    (23, '23', 'conversation owner',                   'SQL', 'V23__conversation_owner.sql',                  NULL, 'init', 0, 1),
    (24, '24', 'feishu thread',                        'SQL', 'V24__feishu_thread.sql',                       NULL, 'init', 0, 1),
    (25, '25', 'rbac actions and audit',               'SQL', 'V25__rbac_actions_and_audit.sql',              NULL, 'init', 0, 1);
