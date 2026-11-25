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
import com.easywing.platform.aid.domain.dto.AidEpisodeDTO;
import com.easywing.platform.aid.domain.vo.AidEpisodeVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidEpisodeService;
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
 * 分集管理（PRD FR-5）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/episodes")
@Tag(name = "AID 分集")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidEpisodeController {

    private final AidEpisodeService episodeService;

    @GetMapping
    @Operation(summary = "分页查询分集")
    @PreAuthorize("hasAuthority('aid:episode:list')")
    public ResponseEntity<Page<AidEpisodeVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(episodeService.selectPage(current, size, projectId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询分集")
    @PreAuthorize("hasAuthority('aid:episode:query')")
    public ResponseEntity<AidEpisodeVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(episodeService.selectById(id));
    }

    @PostMapping
    @Operation(summary = "新增分集")
    @PreAuthorize("hasAuthority('aid:episode:add')")
    @Log(title = "分集管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#dto.projectId + ':' + #dto.episodeNo", expire = 30, message = "分集正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidEpisodeDTO dto) {
        return ResponseEntity.ok(episodeService.insertEpisode(dto));
    }

    @PutMapping
    @Operation(summary = "修改分集")
    @PreAuthorize("hasAuthority('aid:episode:edit')")
    @Log(title = "分集管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#dto.id", expire = 30, message = "分集正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody AidEpisodeDTO dto) {
        episodeService.updateEpisode(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除分集")
    @PreAuthorize("hasAuthority('aid:episode:remove')")
    @Log(title = "分集管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        episodeService.deleteEpisodes(ids);
        return ResponseEntity.ok().build();
    }
}
