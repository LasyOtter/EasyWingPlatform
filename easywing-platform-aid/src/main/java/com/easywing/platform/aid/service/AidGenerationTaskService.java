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
import com.easywing.platform.aid.domain.dto.AidGenerationTaskDTO;
import com.easywing.platform.aid.domain.entity.AidGenerationTask;
import com.easywing.platform.aid.domain.query.AidGenerationTaskQuery;
import com.easywing.platform.aid.domain.vo.AidGenerationTaskVO;
import java.util.List;

/**
 * 生成任务服务（PRD FR-9 / FR-10）。
 * <p>
 * M1 只做「建单 + 查单」：提交即落库并返回 taskId，调度/消费在 M2 接入队列。
 * 幂等键在此生成，保证同一目标重复提交只产生一条任务（FR-10.7 原子防重）。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidGenerationTaskService extends IService<AidGenerationTask> {

    /**
     * 分页查询任务。
     *
     * @param current 页码
     * @param size    每页大小
     * @param query   过滤条件，可为空
     * @return 分页结果
     */
    Page<AidGenerationTaskVO> selectPage(long current, long size, AidGenerationTaskQuery query);

    /**
     * 按 ID 查询任务。
     *
     * @param id 任务 ID
     * @return 出参
     */
    AidGenerationTaskVO selectById(Long id);

    /**
     * 创建任务记录。
     *
     * @param dto 入参
     * @return 新任务 ID
     */
    Long createTask(AidGenerationTaskDTO dto);

    /**
     * 按幂等键查询已有任务，用于去重判断。
     *
     * @param idempotencyKey 幂等键
     * @return 已有任务，不存在返回 null
     */
    AidGenerationTaskVO selectByIdempotencyKey(String idempotencyKey);

    /**
     * 删除任务（仅允许删除终态任务，避免误删在途任务）。
     *
     * @param ids 任务 ID 列表
     */
    void deleteTasks(List<Long> ids);
}
