INSERT INTO investigator_permission (id, code, name, perm_type, menu_path, sort_order) VALUES
    (UNHEX(REPLACE('a1000000-0000-0000-0000-000000000010','-','')), 'menu.review', '审查', 'MENU', '/review', 45);

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code = 'menu.review';

INSERT INTO investigator_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM investigator_role r
JOIN investigator_permission p
WHERE r.code = 'OPERATOR'
  AND p.code = 'menu.review';
