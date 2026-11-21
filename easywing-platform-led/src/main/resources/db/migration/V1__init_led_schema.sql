-- =============================================
-- EasyWing Platform LED Service Database Migration
-- Version: V1
-- Description: Initialize LED display service tables
-- Author: EasyWing Team
-- =============================================

-- LED 屏元数据表
CREATE TABLE IF NOT EXISTS led_screen (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    screen_id VARCHAR(64) NOT NULL COMMENT '屏ID（与 YAML 文件名一致）',
    screen_name VARCHAR(128) DEFAULT NULL COMMENT '屏名称',
    ip VARCHAR(128) NOT NULL COMMENT '控制器地址（IP/MAC/串口）',
    width INT NOT NULL COMMENT '屏宽（像素）',
    height INT NOT NULL COMMENT '屏高（像素）',
    color_type INT DEFAULT 3 COMMENT '基色：1单色 2双色 3全彩 4X系列',
    gray_level INT DEFAULT 0 COMMENT '灰度等级',
    led_type INT DEFAULT 3 COMMENT '卡型号：0 T/A/U/XC/W,1 E,2 X,3 C,4 E5/E6/C8,5 D2/D4/D6',
    sdk_provider VARCHAR(32) DEFAULT NULL COMMENT 'SDK实现：vision/listen/onbon',
    connection VARCHAR(16) DEFAULT 'tcp' COMMENT '连接方式：tcp/udp/serial/wan',
    last_deploy_status TINYINT DEFAULT NULL COMMENT '最后下发结果：0成功 1部分失败 2全部失败',
    last_deploy_time DATETIME DEFAULT NULL COMMENT '最后下发时间',
    last_error VARCHAR(1000) DEFAULT NULL COMMENT '最后错误信息',
    online TINYINT DEFAULT NULL COMMENT '是否在线：0否 1是',
    program_snapshot TEXT DEFAULT NULL COMMENT '节目配置快照（JSON）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_screen_id (screen_id),
    INDEX idx_sdk_provider (sdk_provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LED屏元数据表';

-- LED 内码刷新绑定表
CREATE TABLE IF NOT EXISTS led_refresh_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    screen_id VARCHAR(64) NOT NULL COMMENT '屏ID',
    program_no INT DEFAULT 0 COMMENT '目标节目号（默认0）',
    area_index INT NOT NULL COMMENT '内码区顺序号（0起）',
    color VARCHAR(16) DEFAULT 'green' COMMENT '颜色名',
    effect INT DEFAULT 0 COMMENT '%disp特效 0-9',
    template VARCHAR(500) DEFAULT '' COMMENT '文本模板（支持{占位符}）',
    source VARCHAR(64) DEFAULT 'counter' COMMENT '数据源：命名源id或内建类型',
    interval VARCHAR(16) DEFAULT '30s' COMMENT '刷新周期',
    order_num INT DEFAULT 0 COMMENT '排序号',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0否 1是',
    counter_start DECIMAL(18,4) DEFAULT NULL COMMENT 'counter源起始值',
    counter_step DECIMAL(18,4) DEFAULT NULL COMMENT 'counter源步长',
    counter_decimals INT DEFAULT NULL COMMENT 'counter源小数位',
    now_pattern VARCHAR(64) DEFAULT NULL COMMENT 'now源格式',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_screen_id (screen_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LED内码刷新绑定表';

-- LED 命名数据源表
CREATE TABLE IF NOT EXISTS led_data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    source_id VARCHAR(64) NOT NULL COMMENT '数据源ID',
    type VARCHAR(32) NOT NULL COMMENT '类型：jdbc/http/counter/now/static/script',
    params TEXT DEFAULT NULL COMMENT '连接参数（JSON）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_source_id (source_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LED命名数据源表';
