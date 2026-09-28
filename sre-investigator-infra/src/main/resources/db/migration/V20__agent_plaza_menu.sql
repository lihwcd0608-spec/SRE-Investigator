INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000011','-','')), 'menu.agents', '子 Agent广场', 'MENU', '/agents', 32);

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE p.code = 'menu.agents'
  AND r.code IN ('SUPER_ADMIN', 'OPERATOR');

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT rp.role_id, p.id
FROM investigator_role_permission rp
JOIN investigator_permission bindings
    ON bindings.id = rp.permission_id AND bindings.code = 'menu.bindings'
JOIN investigator_permission p
    ON p.code = 'menu.agents'
WHERE NOT EXISTS (
    SELECT 1
    FROM investigator_role_permission existing
    WHERE existing.role_id = rp.role_id
      AND existing.permission_id = p.id
);
