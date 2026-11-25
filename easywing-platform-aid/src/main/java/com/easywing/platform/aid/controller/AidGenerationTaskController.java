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
package com.easywing.platform.aid.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.aid.annotation.Log;
import com.easywing.platform.aid.domain.dto.AidGenerationTaskDTO;
import com.easywing.platform.aid.domain.query.AidGenerationTaskQuery;
import com.easywing.platform.aid.domain.vo.AidGenerationTaskVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidGenerationTaskService;
import com.easywing.platform.web.idempotent.Idempotent;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生成任务管理（PRD FR-9 / FR-10）。
 * <p>
 * M1 只落库返回 taskId，实际入队与消费在 M2 接入队列后生效；
 * 幂等由服务层按 {@code targetType:targetId:taskType} 保证。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/tasks")
@Tag(name = "AID 生成任务")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidGenerationTaskController {

    private final AidGenerationTaskService taskService;

    @GetMapping
    @Operation(summary = "分页查询生成任务")
    @PreAuthorize("hasAuthority('aid:task:list')")
    public ResponseEntity<Page<AidGenerationTaskVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            AidGenerationTaskQuery query) {
        return ResponseEntity.ok(taskService.selectPage(current, size, query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询任务")
    @PreAuthorize("hasAuthority('aid:task:query')")
    public ResponseEntity<AidGenerationTaskVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.selectById(id));
    }

    /** 按幂等键查询，供前端在重复点击后恢复已有任务。 */
    @GetMapping("/by-key")
    @Operation(summary = "按幂等键查询任务")
    @PreAuthorize("hasAuthority('aid:task:query')")
    public ResponseEntity<AidGenerationTaskVO> getByIdempotencyKey(
            @Parameter(description = "幂等键") @RequestParam String key) {
        AidGenerationTaskVO task = taskService.selectByIdempotencyKey(key);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @PostMapping
    @Operation(summary = "创建生成任务")
    @PreAuthorize("hasAuthority('aid:task:add')")
    @Log(title = "生成任务", businessType = BusinessType.INSERT)
    @Idempotent(
            key = "#dto.targetType + ':' + #dto.targetId + ':' + #dto.taskType",
            expire = 30,
            message = "任务正在提交中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidGenerationTaskDTO dto) {
        return ResponseEntity.ok(taskService.createTask(dto));
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除终态任务")
    @PreAuthorize("hasAuthority('aid:task:remove')")
    @Log(title = "生成任务", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        taskService.deleteTasks(ids);
        return ResponseEntity.ok().build();
    }
}
