-- =============================================
-- EasyWing Platform System Service Initial Data
-- Version: V2
-- Description: Insert initial data for system management
-- Author: EasyWing Team
-- Date: 2024
-- =============================================

-- Insert default department
INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status, create_by) VALUES
(1, 0, '0', '总公司', 0, 'Admin', '15888888888', 'admin@easywing.io', 0, 'admin');

-- Insert default user (password: admin123, BCrypt encoded)
INSERT INTO sys_user (id, dept_id, username, nickname, user_type, email, phone, gender, password, status, create_by) VALUES
(1, 1, 'admin', '超级管理员', '00', 'admin@easywing.io', '15888888888', '1', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 0, 'admin');

-- Insert default roles
INSERT INTO sys_role (id, role_name, role_key, role_sort, data_scope, status, create_by) VALUES
(1, '超级管理员', 'super_admin', 0, 1, 0, 'admin'),
(2, '系统管理员', 'admin', 1, 2, 0, 'admin'),
(3, '普通用户', 'user', 2, 5, 0, 'admin');

-- Insert default menus
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by) VALUES
(1, '系统管理', 0, 1, 'system', NULL, NULL, '', 1, 0, 'M', 0, 0, '', 'system', 'admin'),
(2, '用户管理', 1, 1, 'user', 'system/user/index', NULL, '', 1, 0, 'C', 0, 0, 'system:user:list', 'user', 'admin'),
(3, '角色管理', 1, 2, 'role', 'system/role/index', NULL, '', 1, 0, 'C', 0, 0, 'system:role:list', 'peoples', 'admin'),
(4, '菜单管理', 1, 3, 'menu', 'system/menu/index', NULL, '', 1, 0, 'C', 0, 0, 'system:menu:list', 'tree-table', 'admin'),
(5, '部门管理', 1, 4, 'dept', 'system/dept/index', NULL, '', 1, 0, 'C', 0, 0, 'system:dept:list', 'tree', 'admin'),
(6, '字典管理', 1, 6, 'dict', 'system/dict/index', NULL, '', 1, 0, 'C', 0, 0, 'system:dict:list', 'dict', 'admin'),
(7, '岗位管理', 1, 5, 'post', 'system/post/index', NULL, '', 1, 0, 'C', 0, 0, 'system:post:list', 'post', 'admin'),
(8, '日志管理', 1, 7, 'log', NULL, NULL, '', 1, 0, 'M', 0, 0, '', 'log', 'admin'),
(9, '操作日志', 8, 1, 'operlog', 'system/operlog/index', NULL, '', 1, 0, 'C', 0, 0, 'system:operlog:list', 'form', 'admin'),
(10, '登录日志', 8, 2, 'logininfor', 'system/logininfor/index', NULL, '', 1, 0, 'C', 0, 0, 'system:logininfor:list', 'logininfor', 'admin');

-- Insert default posts
INSERT INTO sys_post (id, post_code, post_name, post_sort, status, create_by) VALUES
(1, 'ceo', '董事长', 1, 0, 'admin'),
(2, 'manager', '项目经理', 2, 0, 'admin'),
(3, 'developer', '开发人员', 3, 0, 'admin'),
(4, 'tester', '测试人员', 4, 0, 'admin');

-- Insert default dictionary types
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, create_by) VALUES
(1, '用户性别', 'sys_user_sex', 0, 'admin'),
(2, '菜单状态', 'sys_show_hide', 0, 'admin'),
(3, '系统开关', 'sys_normal_disable', 0, 'admin'),
(4, '任务状态', 'sys_job_status', 0, 'admin'),
(5, '任务分组', 'sys_job_group', 0, 'admin'),
(6, '通知类型', 'sys_notice_type', 0, 'admin'),
(7, '通知状态', 'sys_notice_status', 0, 'admin'),
(8, '操作类型', 'sys_oper_type', 0, 'admin'),
(9, '系统是否', 'sys_yes_no', 0, 'admin');

-- Insert default dictionary data
INSERT INTO sys_dict_data (id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by) VALUES
(1, 1, '男', '1', 'sys_user_sex', '', 'primary', 1, 0, 'admin'),
(2, 2, '女', '2', 'sys_user_sex', '', 'danger', 0, 0, 'admin'),
(3, 3, '未知', '0', 'sys_user_sex', '', 'info', 0, 0, 'admin'),
(4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 1, 0, 'admin'),
(5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 0, 0, 'admin'),
(6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 1, 0, 'admin'),
(7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 0, 0, 'admin'),
(8, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 1, 0, 'admin'),
(9, 2, '否', 'N', 'sys_yes_no', '', 'danger', 0, 0, 'admin'),
(10, 1, '新增', '1', 'sys_oper_type', '', 'info', 0, 0, 'admin'),
(11, 2, '修改', '2', 'sys_oper_type', '', 'info', 0, 0, 'admin'),
(12, 3, '删除', '3', 'sys_oper_type', '', 'danger', 0, 0, 'admin'),
(13, 4, '授权', '4', 'sys_oper_type', '', 'primary', 0, 0, 'admin'),
(14, 5, '导出', '5', 'sys_oper_type', '', 'warning', 0, 0, 'admin'),
(15, 6, '导入', '6', 'sys_oper_type', '', 'warning', 0, 0, 'admin');

-- Insert user-role relation
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- Insert role-menu relation (super admin has all menus)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (1, 10);
