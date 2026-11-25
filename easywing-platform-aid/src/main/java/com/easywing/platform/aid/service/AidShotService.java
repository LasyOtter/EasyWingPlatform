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
import com.easywing.platform.aid.domain.dto.AidShotDTO;
import com.easywing.platform.aid.domain.entity.AidShot;
import com.easywing.platform.aid.domain.vo.AidShotVO;
import java.util.List;

/**
 * 分镜服务（PRD FR-8）。
 * <p>M1 只提供录入与查询；{@code promptFinal} 的回填由 M2 的生成流程负责。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidShotService extends IService<AidShot> {

    /**
     * 分页查询分镜，可按分集过滤。
     *
     * @param current   页码
     * @param size      每页大小
     * @param episodeId 分集 ID，可为空
     * @return 分页结果
     */
    Page<AidShotVO> selectPage(long current, long size, Long episodeId);

    /**
     * 按 ID 查询分镜。
     *
     * @param id 分镜 ID
     * @return 出参
     */
    AidShotVO selectById(Long id);

    /**
     * 新增分镜。
     *
     * @param dto 入参
     * @return 新分镜 ID
     */
    Long insertShot(AidShotDTO dto);

    /**
     * 修改分镜。
     *
     * @param dto 入参，必须携带 id
     */
    void updateShot(AidShotDTO dto);

    /**
     * 删除分镜。
     *
     * @param ids 分镜 ID 列表
     */
    void deleteShots(List<Long> ids);
}
