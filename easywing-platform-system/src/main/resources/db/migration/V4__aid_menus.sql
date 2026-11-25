-- =============================================
-- AID 漫剧工坊菜单与权限种子（M1）
-- 说明：
--   1. 菜单 ID 从 9000 起，避开系统管理已占用的 1~10 以及后续可能新增的编号；
--   2. 只授权给「超级管理员」(role_id=1)，与 V2 中 super admin 拥有全部菜单的语义一致；
--   3. 使用 NOT EXISTS 保证脚本可重复执行，不依赖 Flyway 的 repeatable 语义。
-- =============================================

-- 一级目录：AI 创作
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9000, 'AI 创作', 0, 90, 'aid', NULL, NULL, '', 1, 0, 'M', 0, 0, '', 'tool', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9000);

-- 项目管理
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9001, '项目管理', 9000, 1, 'project', 'aid/project/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:project:list', 'documentation', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9001);

INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9002, '项目查询', 9001, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:project:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9002);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9003, '项目新增', 9001, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:project:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9003);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9004, '项目修改', 9001, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:project:edit', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9004);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9005, '项目删除', 9001, 4, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:project:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9005);

-- 资产库
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9010, '资产库', 9000, 2, 'asset', 'aid/asset/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:asset:list', 'image', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9010);

INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9011, '资产查询', 9010, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:asset:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9011);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9012, '资产新增', 9010, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:asset:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9012);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9013, '资产修改', 9010, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:asset:edit', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9013);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9014, '资产删除', 9010, 4, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:asset:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9014);

-- 视觉设定
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9020, '视觉设定', 9000, 3, 'visual-setting', 'aid/visual-setting/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:visual:list', 'color', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9020);

INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9021, '设定查询', 9020, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:visual:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9021);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9022, '设定新增', 9020, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:visual:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9022);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9023, '设定修改', 9020, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:visual:edit', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9023);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9024, '设定删除', 9020, 4, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:visual:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9024);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9025, '设定导出', 9020, 5, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:visual:export', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9025);

-- 分集 / 分镜 / 生成任务（M1 已建表、接口与前端页面）
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9030, '分集管理', 9000, 4, 'episode', 'aid/episode/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:episode:list', 'list', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9030);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9031, '分集查询', 9030, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:episode:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9031);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9032, '分集新增', 9030, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:episode:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9032);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9033, '分集修改', 9030, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:episode:edit', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9033);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9034, '分集删除', 9030, 4, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:episode:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9034);

INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9040, '分镜管理', 9000, 5, 'shot', 'aid/shot/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:shot:list', 'film', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9040);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9041, '分镜查询', 9040, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:shot:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9041);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9042, '分镜新增', 9040, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:shot:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9042);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9043, '分镜修改', 9040, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:shot:edit', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9043);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9044, '分镜删除', 9040, 4, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:shot:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9044);

INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9050, '生成任务', 9000, 6, 'task', 'aid/task/index', NULL, '', 1, 0, 'C', 0, 0, 'aid:task:list', 'jobs', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9050);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9051, '任务查询', 9050, 1, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:task:query', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9051);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9052, '任务新增', 9050, 2, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:task:add', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9052);
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by)
SELECT 9053, '任务删除', 9050, 3, '', NULL, NULL, '', 1, 0, 'F', 0, 0, 'aid:task:remove', '#', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 9053);

-- 授予超级管理员（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id BETWEEN 9000 AND 9053
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
