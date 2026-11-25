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
import com.easywing.platform.aid.domain.dto.AidProjectDTO;
import com.easywing.platform.aid.domain.query.AidProjectQuery;
import com.easywing.platform.aid.domain.vo.AidProjectVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidProjectService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作项目管理（PRD FR-1 ~ FR-3）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/projects")
@Tag(name = "AID 项目管理")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidProjectController {

    private final AidProjectService projectService;

    @GetMapping
    @Operation(summary = "分页查询项目")
    @PreAuthorize("hasAuthority('aid:project:list')")
    public ResponseEntity<Page<AidProjectVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            AidProjectQuery query) {
        return ResponseEntity.ok(projectService.selectProjectPage(current, size, query));
    }

    @GetMapping("/options")
    @Operation(summary = "项目下拉选项")
    @PreAuthorize("hasAuthority('aid:project:list')")
    public ResponseEntity<List<AidProjectVO>> options() {
        return ResponseEntity.ok(projectService.selectProjectOptions());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询项目")
    @PreAuthorize("hasAuthority('aid:project:query')")
    public ResponseEntity<AidProjectVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.selectProjectById(id));
    }

    @PostMapping
    @Operation(summary = "新增项目")
    @PreAuthorize("hasAuthority('aid:project:add')")
    @Log(title = "项目管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#dto.name", expire = 30, message = "项目正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidProjectDTO dto) {
        return ResponseEntity.ok(projectService.insertProject(dto));
    }

    @PutMapping
    @Operation(summary = "修改项目")
    @PreAuthorize("hasAuthority('aid:project:edit')")
    @Log(title = "项目管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#dto.id", expire = 30, message = "项目正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody AidProjectDTO dto) {
        projectService.updateProject(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除项目")
    @PreAuthorize("hasAuthority('aid:project:remove')")
    @Log(title = "项目管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        projectService.deleteProjects(ids);
        return ResponseEntity.ok().build();
    }
}
