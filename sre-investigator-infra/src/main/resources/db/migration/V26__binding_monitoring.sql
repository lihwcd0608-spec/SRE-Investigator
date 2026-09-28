-- 工具绑定监控：binding 增加监控开关与指标配置；新增时序指标表；新增 action.bindings.monitor 权限

-- 1. binding 表增加监控字段
ALTER TABLE investigator_tool_binding
    ADD COLUMN monitoring_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启监控' AFTER tags,
    ADD COLUMN monitoring_metrics LONGTEXT NULL COMMENT '监控指标列表 JSON，如 ["connections","memory","slow_queries","threads_running","qps"]' AFTER monitoring_enabled;

-- 2. 时序指标快照表
CREATE TABLE investigator_binding_metric (
    id BINARY(16) NOT NULL,
    binding_id BINARY(16) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    metric_value VARCHAR(128) NULL,
    collected_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_binding_metric_time (binding_id, metric_key, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具绑定监控指标时序快照';

-- 3. 监控权限
INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a2000000-0000-0000-0000-000000000015','-','')), 'action.bindings.monitor', '工具-开启监控', 'ACTION', NULL, 132);

-- 超级管理员获得监控权限
INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code = 'action.bindings.monitor'
  AND NOT EXISTS (
      SELECT 1 FROM investigator_role_permission existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );
