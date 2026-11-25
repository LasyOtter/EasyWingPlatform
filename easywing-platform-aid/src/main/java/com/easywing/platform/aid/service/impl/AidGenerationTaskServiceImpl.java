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
package com.easywing.platform.aid.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.aid.domain.dto.AidGenerationTaskDTO;
import com.easywing.platform.aid.domain.entity.AidGenerationTask;
import com.easywing.platform.aid.domain.query.AidGenerationTaskQuery;
import com.easywing.platform.aid.domain.vo.AidGenerationTaskVO;
import com.easywing.platform.aid.enums.TaskStatus;
import com.easywing.platform.aid.mapper.AidGenerationTaskMapper;
import com.easywing.platform.aid.mapper.struct.AidGenerationTaskStructMapper;
import com.easywing.platform.aid.service.AidGenerationTaskService;
import com.easywing.platform.aid.task.TaskMessage;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 生成任务服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidGenerationTaskServiceImpl extends ServiceImpl<AidGenerationTaskMapper, AidGenerationTask>
        implements AidGenerationTaskService {

    private final AidGenerationTaskMapper taskMapper;
    private final AidGenerationTaskStructMapper structMapper;

    @Override
    public Page<AidGenerationTaskVO> selectPage(long current, long size, AidGenerationTaskQuery query) {
        LambdaQueryWrapper<AidGenerationTask> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(
                            StringUtils.hasText(query.getTargetType()),
                            AidGenerationTask::getTargetType,
                            query.getTargetType())
                    .eq(query.getTargetId() != null, AidGenerationTask::getTargetId, query.getTargetId())
                    .eq(StringUtils.hasText(query.getTaskType()), AidGenerationTask::getTaskType, query.getTaskType())
                    .eq(StringUtils.hasText(query.getStatus()), AidGenerationTask::getStatus, query.getStatus())
                    .eq(
                            StringUtils.hasText(query.getIdempotencyKey()),
                            AidGenerationTask::getIdempotencyKey,
                            query.getIdempotencyKey());
        }
        wrapper.orderByDesc(AidGenerationTask::getCreateTime);

        Page<AidGenerationTask> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        Page<AidGenerationTaskVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(structMapper::toVO).toList());
        return result;
    }

    @Override
    public AidGenerationTaskVO selectById(Long id) {
        AidGenerationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.AID_TASK_NOT_FOUND, "任务不存在: " + id);
        }
        return structMapper.toVO(task);
    }

    @Override
    public AidGenerationTaskVO selectByIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        List<AidGenerationTask> matches = taskMapper.selectList(new LambdaQueryWrapper<AidGenerationTask>()
                .eq(AidGenerationTask::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
        return matches.isEmpty() ? null : structMapper.toVO(matches.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(AidGenerationTaskDTO dto) {
        TaskMessage.TaskType type = parseTaskType(dto.getTaskType());

        // 幂等键：同一目标 + 同一逻辑任务类型只应产生一条任务（FR-10.7）
        String idempotencyKey = buildIdempotencyKey(dto.getTargetType(), dto.getTargetId(), type);
        AidGenerationTaskVO existing = selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            log.info("命中幂等键，返回已有任务: key={}, taskId={}", idempotencyKey, existing.getId());
            return existing.getId();
        }

        AidGenerationTask entity = structMapper.toEntity(dto);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus(TaskStatus.PENDING.getCode());
        entity.setProgress(0);
        entity.setRetryCount(0);
        taskMapper.insert(entity);
        log.info(
                "创建生成任务成功: id={}, type={}, target={}:{}", entity.getId(), type, dto.getTargetType(), dto.getTargetId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 在途任务不允许删除：删了会让队列里的消息取不到记录而悬挂
        List<AidGenerationTask> tasks = taskMapper.selectBatchIds(ids);
        for (AidGenerationTask task : tasks) {
            if (!TaskStatus.of(task.getStatus()).isTerminal()) {
                throw new BizException(ErrorCode.AID_TASK_STATE_INVALID, "任务未结束，不允许删除: " + task.getId());
            }
        }
        taskMapper.deleteBatchIds(ids);
        log.info("删除终态任务 {} 个", ids.size());
    }

    /**
     * 构造幂等键。
     * <p>格式 {@code 项目:剧集:逻辑任务类型:目标}；M1 阶段项目/剧集由 targetType+targetId 间接表达，
     * 因此这里用 {@code targetType:targetId:type} 三元组保证同目标同类任务唯一。</p>
     *
     * @param targetType 关联对象类型
     * @param targetId   关联对象 ID
     * @param type       任务类型
     * @return 幂等键
     */
    private static String buildIdempotencyKey(String targetType, Long targetId, TaskMessage.TaskType type) {
        return targetType + ":" + targetId + ":" + type.name();
    }

    private static TaskMessage.TaskType parseTaskType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "任务类型不能为空");
        }
        try {
            return TaskMessage.TaskType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "未知的任务类型: " + raw);
        }
    }
}
