-- AID 漫剧工坊 M1 骨架：项目 / 分集 / 资产 / 分镜 / 生成任务
-- 公共列约定与 sys_* 表保持一致：id(assign_id) + deleted/create_by/create_time/update_by/update_time/tenant_id

-- 创作项目表
CREATE TABLE IF NOT EXISTS aid_project (
    id BIGINT PRIMARY KEY COMMENT '项目ID',
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '项目简介',
    cover_url VARCHAR(500) DEFAULT NULL COMMENT '封面图 URL',
    visual_setting_id BIGINT DEFAULT NULL COMMENT '关联的视觉设定 ID，创建后可改绑',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '项目状态：DRAFT/RUNNING/PAUSED/COMPLETED/ARCHIVED',
    target_duration INT DEFAULT NULL COMMENT '目标总时长（秒）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作项目';

-- 分集表
CREATE TABLE IF NOT EXISTS aid_episode (
    id BIGINT PRIMARY KEY COMMENT '分集ID',
    project_id BIGINT NOT NULL COMMENT '所属项目ID',
    episode_no INT NOT NULL COMMENT '集号，从 1 开始',
    title VARCHAR(100) DEFAULT NULL COMMENT '分集标题',
    script TEXT DEFAULT NULL COMMENT '剧本文本',
    duration INT DEFAULT NULL COMMENT '目标时长（秒）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/GENERATING/COMPLETED/FAILED',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_project_episode (project_id, episode_no),
    INDEX idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分集';

-- 视觉设定表（模板）
CREATE TABLE IF NOT EXISTS aid_visual_setting (
    id BIGINT PRIMARY KEY COMMENT '视觉设定ID',
    name VARCHAR(100) NOT NULL COMMENT '设定名称',
    scope VARCHAR(20) DEFAULT 'PROJECT' COMMENT '作用域：PROJECT（项目级）/TEMPLATE（可复用模板）',
    style_description VARCHAR(1000) DEFAULT NULL COMMENT '风格描述主文本（硬约束层内容锚）',
    color_palette JSON DEFAULT NULL COMMENT '色盘声明列表（中文+十六进制），逐字引用到提示词',
    sub_styles JSON DEFAULT NULL COMMENT '子风格标签族',
    constraints_list JSON DEFAULT NULL COMMENT '约束条件（强命令式正向要求）',
    negative_words JSON DEFAULT NULL COMMENT '禁用词/负向清单',
    aspect_ratio VARCHAR(20) DEFAULT NULL COMMENT '画幅，如 9:16 / 16:9',
    tail_frame_subject VARCHAR(500) DEFAULT NULL COMMENT '尾帧规范·主体',
    tail_frame_background VARCHAR(500) DEFAULT NULL COMMENT '尾帧规范·背景',
    tail_frame_light VARCHAR(500) DEFAULT NULL COMMENT '尾帧规范·光线方向',
    tail_frame_composition VARCHAR(500) DEFAULT NULL COMMENT '尾帧规范·构图',
    tail_frame_mood VARCHAR(500) DEFAULT NULL COMMENT '尾帧规范·氛围',
    tail_frame_colors JSON DEFAULT NULL COMMENT '尾帧必须出现的颜色名',
    snapshot_time BIGINT DEFAULT 0 COMMENT '快照时间戳（毫秒）',
    version INT DEFAULT 1 COMMENT '版本号，每次修改 +1，下游产物据此判定失效',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视觉设定';

-- 资产库表
CREATE TABLE IF NOT EXISTS aid_asset (
    id BIGINT PRIMARY KEY COMMENT '资产ID',
    project_id BIGINT NOT NULL COMMENT '所属项目ID',
    asset_code VARCHAR(50) NOT NULL COMMENT '资产编号（如 C01/S01/P01），项目内唯一',
    asset_type VARCHAR(20) DEFAULT 'CHARACTER' COMMENT '资产类型：CHARACTER/SCENE/PROP/STYLE',
    name VARCHAR(100) DEFAULT NULL COMMENT '资产名称',
    description VARCHAR(1000) DEFAULT NULL COMMENT '资产描述（供模型理解的人物/场景特征）',
    reference_url VARCHAR(500) DEFAULT NULL COMMENT '参考图 URL',
    prompt_template TEXT DEFAULT NULL COMMENT '该资产专属提示词模板（可覆盖项目级设定）',
    ext_info JSON DEFAULT NULL COMMENT '扩展信息（如角色别名、出现集数）',
    version INT DEFAULT 1 COMMENT '版本号',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_project_code (project_id, asset_code),
    INDEX idx_project_id (project_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产库';

-- 分镜表
CREATE TABLE IF NOT EXISTS aid_shot (
    id BIGINT PRIMARY KEY COMMENT '分镜ID',
    episode_id BIGINT NOT NULL COMMENT '所属分集ID',
    shot_no INT NOT NULL COMMENT '镜号，从 1 开始',
    description TEXT DEFAULT NULL COMMENT '画面描述',
    dialogue TEXT DEFAULT NULL COMMENT '台词',
    camera_movement VARCHAR(200) DEFAULT NULL COMMENT '镜头语言（如 跟拍/推近/固定）',
    duration INT DEFAULT NULL COMMENT '时长（秒）',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '分镜图 URL',
    video_url VARCHAR(500) DEFAULT NULL COMMENT '视频 URL',
    prompt_final TEXT DEFAULT NULL COMMENT '最终提示词（审计用，由 PromptCompiler 产出）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/GENERATING/COMPLETED/FAILED',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    UNIQUE KEY uk_episode_shot (episode_id, shot_no),
    INDEX idx_episode_id (episode_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分镜';

-- 生成任务表
CREATE TABLE IF NOT EXISTS aid_generation_task (
    id BIGINT PRIMARY KEY COMMENT '任务ID',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型：IMAGE/VIDEO/AUDIO/COMPOSE',
    target_type VARCHAR(30) DEFAULT NULL COMMENT '关联对象类型：SHOT/ASSET/EPISODE',
    target_id BIGINT DEFAULT NULL COMMENT '关联对象ID',
    provider VARCHAR(50) DEFAULT NULL COMMENT '使用的 Provider',
    model VARCHAR(100) DEFAULT NULL COMMENT '使用的模型标识',
    prompt TEXT DEFAULT NULL COMMENT '最终提交的提示词快照',
    idempotency_key VARCHAR(200) DEFAULT NULL COMMENT '幂等键：项目:剧集:逻辑任务类型:目标',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/COMPLETED/FAILED/CANCELED',
    progress INT DEFAULT 0 COMMENT '进度百分比 0-100',
    result_url VARCHAR(500) DEFAULT NULL COMMENT '产物 URL',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    cost_amount DECIMAL(10,4) DEFAULT NULL COMMENT '实际费用',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    queue_entry_id VARCHAR(100) DEFAULT NULL COMMENT 'Redis Stream entryId，用于对账',
    visual_snapshot_version INT DEFAULT NULL COMMENT '提交时固化的视觉快照版本',
    started_at DATETIME DEFAULT NULL COMMENT '开始执行时间',
    completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标志（0未删除 1已删除）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status),
    INDEX idx_target (target_type, target_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务';
