-- =============================================
-- EasyWing Platform System Service Database Migration
-- Version: V1
-- Description: Initialize system management tables
-- Author: EasyWing Team
-- Date: 2024
-- =============================================

-- Create sys_dept table (部门表)
CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '部门ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父部门ID',
    ancestors VARCHAR(500) DEFAULT '' COMMENT '祖级列表',
    dept_name VARCHAR(50) NOT NULL COMMENT '部门名称',
    order_num INT DEFAULT 0 COMMENT '显示顺序',
    leader VARCHAR(20) DEFAULT NULL COMMENT '负责人',
    phone VARCHAR(11) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(50) DEFAULT NULL COMMENT '邮箱',
    status TINYINT DEFAULT 0 COMMENT '部门状态（0正常 1停用）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- Create sys_user table (用户表)
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    dept_id BIGINT DEFAULT NULL COMMENT '部门ID',
    username VARCHAR(50) NOT NULL COMMENT '用户账号',
    nickname VARCHAR(50) NOT NULL COMMENT '用户昵称',
    user_type VARCHAR(10) DEFAULT '00' COMMENT '用户类型（00系统用户）',
    email VARCHAR(50) DEFAULT '' COMMENT '用户邮箱',
    phone VARCHAR(11) DEFAULT '' COMMENT '手机号码',
    gender CHAR(1) DEFAULT '0' COMMENT '用户性别（0未知 1男 2女）',
    avatar VARCHAR(200) DEFAULT '' COMMENT '头像地址',
    password VARCHAR(200) DEFAULT '' COMMENT '密码',
    status TINYINT DEFAULT 0 COMMENT '帐号状态（0正常 1停用）',
    login_ip VARCHAR(128) DEFAULT '' COMMENT '最后登录IP',
    login_date DATETIME DEFAULT NULL COMMENT '最后登录时间',
    pwd_update_time DATETIME DEFAULT NULL COMMENT '密码最后更新时间',
    pwd_expire_time DATETIME DEFAULT NULL COMMENT '密码过期时间',
    mfa_enabled TINYINT DEFAULT 0 COMMENT '是否启用MFA（0否 1是）',
    mfa_secret VARCHAR(100) DEFAULT NULL COMMENT 'MFA密钥',
    failed_attempts INT DEFAULT 0 COMMENT '登录失败次数',
    locked_until DATETIME DEFAULT NULL COMMENT '锁定截止时间',
    remark VARCHAR(500) DEFAULT '' COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_username (username),
    INDEX idx_dept_id (dept_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- Create sys_role table (角色表)
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_key VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
    role_sort INT DEFAULT 0 COMMENT '显示顺序',
    data_scope TINYINT DEFAULT 1 COMMENT '数据范围（1：全部数据权限 2：自定义数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
    status TINYINT DEFAULT 0 COMMENT '角色状态（0正常 1停用）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_role_key (role_key),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色信息表';

-- Create sys_menu table (菜单表)
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    order_num INT DEFAULT 0 COMMENT '显示顺序',
    path VARCHAR(200) DEFAULT '' COMMENT '路由地址',
    component VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
    query VARCHAR(255) DEFAULT NULL COMMENT '路由参数',
    route_name VARCHAR(50) DEFAULT '' COMMENT '路由名称',
    is_frame TINYINT DEFAULT 1 COMMENT '是否为外链（0是 1否）',
    is_cache TINYINT DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
    menu_type CHAR(1) DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
    visible TINYINT DEFAULT 0 COMMENT '菜单状态（0显示 1隐藏）',
    status TINYINT DEFAULT 0 COMMENT '菜单状态（0正常 1停用）',
    perms VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    icon VARCHAR(100) DEFAULT '#' COMMENT '菜单图标',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

-- Create sys_post table (岗位表)
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '岗位ID',
    post_code VARCHAR(64) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(50) NOT NULL COMMENT '岗位名称',
    post_sort INT DEFAULT 0 COMMENT '显示顺序',
    status TINYINT DEFAULT 0 COMMENT '状态（0正常 1停用）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_post_code (post_code),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位信息表';

-- Create sys_dict_type table (字典类型表)
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典主键',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型',
    status TINYINT DEFAULT 0 COMMENT '状态（0正常 1停用）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_dict_type (dict_type),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- Create sys_dict_data table (字典数据表)
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典编码',
    dict_sort INT DEFAULT 0 COMMENT '字典排序',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典键值',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型',
    css_class VARCHAR(100) DEFAULT NULL COMMENT '样式属性',
    list_class VARCHAR(100) DEFAULT NULL COMMENT '表格回显样式',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认（0否 1是）',
    status TINYINT DEFAULT 0 COMMENT '状态（0正常 1停用）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_dict_type (dict_type),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- Create sys_user_role table (用户和角色关联表)
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和角色关联表';

-- Create sys_role_menu table (角色和菜单关联表)
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和菜单关联表';

-- Create sys_oper_log table (操作日志记录)
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志主键',
    title VARCHAR(50) DEFAULT '' COMMENT '模块标题',
    business_type TINYINT DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    method VARCHAR(200) DEFAULT '' COMMENT '方法名称',
    request_method VARCHAR(10) DEFAULT '' COMMENT '请求方式',
    operator_type TINYINT DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
    oper_name VARCHAR(50) DEFAULT '' COMMENT '操作人员',
    dept_name VARCHAR(50) DEFAULT '' COMMENT '部门名称',
    oper_url VARCHAR(500) DEFAULT '' COMMENT '请求URL',
    oper_ip VARCHAR(128) DEFAULT '' COMMENT '主机地址',
    oper_location VARCHAR(255) DEFAULT '' COMMENT '操作地点',
    oper_param TEXT DEFAULT NULL COMMENT '请求参数',
    json_result TEXT DEFAULT NULL COMMENT '返回参数',
    status TINYINT DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
    error_msg VARCHAR(2000) DEFAULT '' COMMENT '错误消息',
    oper_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_oper_time (oper_time),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志记录';

-- Create sys_login_log table (系统访问记录)
CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '访问ID',
    username VARCHAR(50) DEFAULT '' COMMENT '用户账号',
    ipaddr VARCHAR(128) DEFAULT '' COMMENT '登录IP地址',
    login_location VARCHAR(255) DEFAULT '' COMMENT '登录地点',
    browser VARCHAR(50) DEFAULT '' COMMENT '浏览器类型',
    os VARCHAR(50) DEFAULT '' COMMENT '操作系统',
    status TINYINT DEFAULT 0 COMMENT '登录状态（0成功 1失败）',
    msg VARCHAR(255) DEFAULT '' COMMENT '提示消息',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_login_time (login_time),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统访问记录';
