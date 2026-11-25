/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.aid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分集（PRD FR-5）。
 * <p>同一项目内 {@code episodeNo} 唯一，由唯一索引 {@code uk_project_episode} 保证。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aid_episode")
public class AidEpisode extends BaseEntity {

    /** 所属项目 ID */
    private Long projectId;

    /** 集号，从 1 开始 */
    private Integer episodeNo;

    /** 分集标题 */
    private String title;

    /** 剧本文本 */
    private String script;

    /** 目标时长（秒） */
    private Integer duration;

    /**
     * 状态。
     * @see com.easywing.platform.aid.enums.EpisodeStatus
     */
    private String status;
}
