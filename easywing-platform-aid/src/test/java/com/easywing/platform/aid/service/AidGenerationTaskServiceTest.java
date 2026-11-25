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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.aid.domain.dto.AidGenerationTaskDTO;
import com.easywing.platform.aid.domain.entity.AidGenerationTask;
import com.easywing.platform.aid.mapper.AidGenerationTaskMapper;
import com.easywing.platform.aid.mapper.struct.AidGenerationTaskStructMapperImpl;
import com.easywing.platform.aid.service.impl.AidGenerationTaskServiceImpl;
import com.easywing.platform.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 生成任务服务单元测试（FR-10.7 幂等）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AidGenerationTaskServiceTest {

    @Mock
    private AidGenerationTaskMapper taskMapper;

    @Spy
    private AidGenerationTaskStructMapperImpl structMapper = new AidGenerationTaskStructMapperImpl();

    @InjectMocks
    private AidGenerationTaskServiceImpl service;

    /** 首次创建：状态 PENDING、进度 0、重试 0、幂等键按 目标+类型 生成。 */
    @Test
    void createInitializesPendingState() {
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.insert(any(AidGenerationTask.class))).thenAnswer(inv -> {
            ((AidGenerationTask) inv.getArgument(0)).setId(42L);
            return 1;
        });

        Long id = service.createTask(dto());

        assertThat(id).isEqualTo(42L);
        ArgumentCaptor<AidGenerationTask> captor = ArgumentCaptor.forClass(AidGenerationTask.class);
        verify(taskMapper).insert(captor.capture());
        AidGenerationTask saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getProgress()).isZero();
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getIdempotencyKey()).isEqualTo("SHOT:7:IMAGE");
    }

    /** FR-10.7：同一目标 + 同一逻辑任务类型重复提交应返回已有任务，不新建。 */
    @Test
    void createIsIdempotentPerTargetAndType() {
        AidGenerationTask existing = new AidGenerationTask();
        existing.setId(9L);
        existing.setStatus("RUNNING");
        when(taskMapper.selectList(any())).thenReturn(List.of(existing));

        Long id = service.createTask(dto());

        assertThat(id).isEqualTo(9L);
        verify(taskMapper, org.mockito.Mockito.never()).insert(any());
    }

    /**
     * 同目标不同类型是不同任务（IMAGE 与 VIDEO 都要跑）。
     * <p>用连续的 stub 返回模拟「第一次查不到、第二次查得到」：幂等键不同，因此互不复用。</p>
     */
    @Test
    void differentTypesAreDistinctTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());
        when(taskMapper.insert(any(AidGenerationTask.class)))
                .thenAnswer(inv -> {
                    ((AidGenerationTask) inv.getArgument(0)).setId(1L);
                    return 1;
                })
                .thenAnswer(inv -> {
                    ((AidGenerationTask) inv.getArgument(0)).setId(2L);
                    return 1;
                });

        assertThat(service.createTask(dto())).isEqualTo(1L);

        AidGenerationTaskDTO video = dto();
        video.setTaskType("VIDEO");
        assertThat(service.createTask(video)).isEqualTo(2L);
    }

    @Test
    void rejectUnknownTaskType() {
        AidGenerationTaskDTO dto = dto();
        dto.setTaskType("HOLOGRAM");

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知的任务类型");
    }

    /** 在途任务不允许删除：删了会让队列消息取不到记录而悬挂。 */
    @Test
    void deleteRejectsInFlightTask() {
        AidGenerationTask running = new AidGenerationTask();
        running.setId(5L);
        running.setStatus("RUNNING");
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.deleteTasks(List.of(5L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未结束");
    }

    @Test
    void deleteAllowsTerminalTask() {
        AidGenerationTask done = new AidGenerationTask();
        done.setId(5L);
        done.setStatus("COMPLETED");
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(done));

        service.deleteTasks(List.of(5L));

        verify(taskMapper).deleteBatchIds(List.of(5L));
    }

    @Test
    void queryByIdempotencyKeyReturnsNullWhenAbsent() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.selectByIdempotencyKey("SHOT:7:IMAGE")).isNull();
    }

    @Test
    void emptyPageReturnsEmptyRecords() {
        Page<AidGenerationTask> raw = new Page<>(1, 10, 0);
        when(taskMapper.selectPage(any(), any())).thenReturn(raw);

        Page<?> result = service.selectPage(1, 10, null);

        assertThat(result.getRecords()).isEmpty();
    }

    private static AidGenerationTaskDTO dto() {
        AidGenerationTaskDTO dto = new AidGenerationTaskDTO();
        dto.setTargetType("SHOT");
        dto.setTargetId(7L);
        dto.setTaskType("IMAGE");
        return dto;
    }
}
