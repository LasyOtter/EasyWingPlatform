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
import com.easywing.platform.aid.domain.dto.AidVisualSettingDTO;
import com.easywing.platform.aid.domain.vo.AidVisualSettingVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidVisualSettingService;
import com.easywing.platform.web.idempotent.Idempotent;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
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
 * 视觉设定管理（PRD FR-4.7 / FR-11，AC-11 模板导出导入）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/visual-settings")
@Tag(name = "AID 视觉设定")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidVisualSettingController {

    private final AidVisualSettingService visualSettingService;

    @GetMapping
    @Operation(summary = "分页查询视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:list')")
    public ResponseEntity<Page<AidVisualSettingVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "作用域：PROJECT/TEMPLATE") @RequestParam(required = false) String scope,
            @Parameter(description = "名称模糊匹配") @RequestParam(required = false) String name) {
        return ResponseEntity.ok(visualSettingService.selectPage(current, size, scope, name));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:query')")
    public ResponseEntity<AidVisualSettingVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(visualSettingService.selectById(id));
    }

    @PostMapping
    @Operation(summary = "新增视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:add')")
    @Log(title = "视觉设定", businessType = BusinessType.INSERT)
    @Idempotent(key = "#dto.name + ':' + #dto.scope", expire = 30, message = "设定正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidVisualSettingDTO dto) {
        return ResponseEntity.ok(visualSettingService.insertSetting(dto));
    }

    @PutMapping
    @Operation(summary = "修改视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:edit')")
    @Log(title = "视觉设定", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#dto.id", expire = 30, message = "设定正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody AidVisualSettingDTO dto) {
        visualSettingService.updateSetting(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:remove')")
    @Log(title = "视觉设定", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        visualSettingService.deleteSettings(ids);
        return ResponseEntity.ok().build();
    }

    /** AC-11：导出为 JSON 模板。 */
    @PostMapping("/{id}/export")
    @Operation(summary = "导出视觉设定为 JSON 模板")
    @PreAuthorize("hasAuthority('aid:visual:export')")
    @Log(title = "视觉设定", businessType = BusinessType.EXPORT)
    public ResponseEntity<Map<String, Object>> export(@PathVariable Long id) {
        return ResponseEntity.ok(visualSettingService.exportTemplate(id));
    }

    /** AC-11：从 JSON 模板导入并生效。 */
    @PostMapping("/import")
    @Operation(summary = "从 JSON 模板导入视觉设定")
    @PreAuthorize("hasAuthority('aid:visual:add')")
    @Log(title = "视觉设定", businessType = BusinessType.IMPORT)
    public ResponseEntity<Long> importTemplate(@RequestBody Map<String, Object> request) {
        Object content = request.get("content");
        if (!(content instanceof Map)) {
            throw new com.easywing.platform.core.exception.BizException(
                    com.easywing.platform.core.exception.ErrorCode.INVALID_PARAMETER, "模板内容缺失，请提供 content 对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> templateJson = (Map<String, Object>) content;
        Object name = request.get("name");
        boolean overwrite = Boolean.parseBoolean(String.valueOf(request.getOrDefault("overwrite", "false")));
        return ResponseEntity.ok(visualSettingService.importTemplate(
                templateJson, name == null ? null : String.valueOf(name), overwrite));
    }
}
