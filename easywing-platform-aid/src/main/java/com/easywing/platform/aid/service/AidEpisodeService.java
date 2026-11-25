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
package com.easywing.platform.aid.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.aid.domain.dto.AidEpisodeDTO;
import com.easywing.platform.aid.domain.entity.AidEpisode;
import java.util.List;

/**
 * 分集服务（PRD FR-5）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidEpisodeService extends IService<AidEpisode> {

    /**
     * 分页查询分集。
     *
     * @param current  页码
     * @param size     每页大小
     * @param projectId 项目 ID
     * @return 分页结果
     */
    Page<com.easywing.platform.aid.domain.vo.AidEpisodeVO> selectPage(long current, long size, Long projectId);

    /**
     * 按 ID 查询分集。
     *
     * @param id 分集 ID
     * @return 出参
     */
    com.easywing.platform.aid.domain.vo.AidEpisodeVO selectById(Long id);

    /**
     * 新增分集。
     *
     * @param dto 入参
     * @return 新分集 ID
     */
    Long insertEpisode(AidEpisodeDTO dto);

    /**
     * 修改分集。
     *
     * @param dto 入参，必须携带 id
     */
    void updateEpisode(AidEpisodeDTO dto);

    /**
     * 删除分集。
     *
     * @param ids 分集 ID 列表
     */
    void deleteEpisodes(List<Long> ids);
}
