-- 标准 RBAC：按钮级 ACTION 权限 + 审计字段 + 审计菜单
-- 工具配置（bindings）的查看与写删仅授予超级管理员；运维角色收回 menu.bindings

ALTER TABLE investigator_audit_event
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OP' AFTER run_id,
    ADD COLUMN actor_user_id BINARY(16) NULL AFTER category,
    ADD COLUMN actor_username VARCHAR(80) NULL AFTER actor_user_id,
    ADD COLUMN client_ip VARCHAR(64) NULL AFTER actor_username,
    ADD COLUMN success TINYINT(1) NULL AFTER client_ip;

UPDATE investigator_audit_event
SET category = CASE
    WHEN action LIKE 'auth.login%' OR action LIKE 'auth.feishu%' OR action = 'auth.logout' THEN 'LOGIN'
    WHEN action LIKE 'tool.%' OR action LIKE 'mcp.%' THEN 'TOOL'
    ELSE 'OP'
END
WHERE category = 'OP';

CREATE INDEX idx_investigator_audit_category_time ON investigator_audit_event (category, occurred_at);
CREATE INDEX idx_investigator_audit_actor_time ON investigator_audit_event (actor_user_id, occurred_at);

INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
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

-- 超级管理员：补齐全部新增权限
INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN (
      'menu.audit',
      'action.projects.write', 'action.projects.delete',
      'action.repositories.write', 'action.repositories.delete',
      'action.bindings.write', 'action.bindings.delete',
      'action.agents.write', 'action.agents.delete',
      'action.automations.write', 'action.automations.delete',
      'action.model.write', 'action.users.write', 'action.roles.write', 'action.feishu.write'
  )
  AND NOT EXISTS (
      SELECT 1 FROM investigator_role_permission existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- 运维：可写项目/代码仓/专家/定时；不可见不可改工具配置；无审计/用户/角色/模型写
INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'OPERATOR'
  AND p.code IN (
      'action.projects.write', 'action.projects.delete',
      'action.repositories.write', 'action.repositories.delete',
      'action.agents.write', 'action.agents.delete',
      'action.automations.write', 'action.automations.delete'
  )
  AND NOT EXISTS (
      SELECT 1 FROM investigator_role_permission existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- 收回运维的工具菜单（仅管理员可见/可配）
DELETE rp
FROM investigator_role_permission rp
JOIN investigator_role r ON r.id = rp.role_id
JOIN investigator_permission p ON p.id = rp.permission_id
WHERE r.code = 'OPERATOR'
  AND p.code = 'menu.bindings';
