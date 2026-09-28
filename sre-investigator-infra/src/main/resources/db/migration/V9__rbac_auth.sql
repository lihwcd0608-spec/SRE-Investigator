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

INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000001','-','')), 'menu.projects', '项目', 'MENU', '/projects', 10),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000002','-','')), 'menu.repositories', '代码仓', 'MENU', '/repositories', 20),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000003','-','')), 'menu.bindings', '工具', 'MENU', '/bindings', 30),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000004','-','')), 'menu.investigate', '对话', 'MENU', '/investigate', 40),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000005','-','')), 'menu.model', '模型', 'MENU', '/settings/model', 50),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000006','-','')), 'menu.users', '用户', 'MENU', '/settings/users', 60),
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000007','-','')), 'menu.roles', '角色', 'MENU', '/settings/roles', 70);

INSERT INTO investigator_role (id, code, name, builtin, description) VALUES
    (UNHEX(REPLACE('b1000000-0000-0000-0000-000000000001','-','')), 'SUPER_ADMIN', '超级管理员', TRUE, '全部菜单，可见全部项目'),
    (UNHEX(REPLACE('b1000000-0000-0000-0000-000000000002','-','')), 'OPERATOR', '运维', TRUE, '项目、代码仓、工具、对话'),
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
  AND p.code IN ('menu.projects', 'menu.repositories', 'menu.bindings', 'menu.investigate');

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'VIEWER'
  AND p.code IN ('menu.projects', 'menu.investigate');
