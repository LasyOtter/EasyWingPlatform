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
import com.easywing.platform.aid.domain.dto.AidShotDTO;
import com.easywing.platform.aid.domain.vo.AidShotVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidShotService;
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
 * 分镜管理（PRD FR-8）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/shots")
@Tag(name = "AID 分镜")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidShotController {

    private final AidShotService shotService;

    @GetMapping
    @Operation(summary = "分页查询分镜")
    @PreAuthorize("hasAuthority('aid:shot:list')")
    public ResponseEntity<Page<AidShotVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "分集ID") @RequestParam(required = false) Long episodeId) {
        return ResponseEntity.ok(shotService.selectPage(current, size, episodeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询分镜")
    @PreAuthorize("hasAuthority('aid:shot:query')")
    public ResponseEntity<AidShotVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(shotService.selectById(id));
    }

    @PostMapping
    @Operation(summary = "新增分镜")
    @PreAuthorize("hasAuthority('aid:shot:add')")
    @Log(title = "分镜管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#dto.episodeId + ':' + #dto.shotNo", expire = 30, message = "分镜正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidShotDTO dto) {
        return ResponseEntity.ok(shotService.insertShot(dto));
    }

    @PutMapping
    @Operation(summary = "修改分镜")
    @PreAuthorize("hasAuthority('aid:shot:edit')")
    @Log(title = "分镜管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#dto.id", expire = 30, message = "分镜正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody AidShotDTO dto) {
        shotService.updateShot(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除分镜")
    @PreAuthorize("hasAuthority('aid:shot:remove')")
    @Log(title = "分镜管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        shotService.deleteShots(ids);
        return ResponseEntity.ok().build();
    }
}
