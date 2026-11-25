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
import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.domain.query.AidAssetQuery;
import com.easywing.platform.aid.domain.vo.AidAssetVO;
import com.easywing.platform.aid.enums.BusinessType;
import com.easywing.platform.aid.service.AidAssetService;
import com.easywing.platform.aid.storage.AssetStorageService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 资产库管理（PRD FR-6，AC-11 资产编号自定义）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid/assets")
@Tag(name = "AID 资产库")
@ApiVersion("v1")
@RequiredArgsConstructor
public class AidAssetController {

    private final AidAssetService assetService;
    private final AssetStorageService storageService;

    @GetMapping
    @Operation(summary = "分页查询资产")
    @PreAuthorize("hasAuthority('aid:asset:list')")
    public ResponseEntity<Page<AidAssetVO>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") @Min(1) long current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            AidAssetQuery query) {
        return ResponseEntity.ok(assetService.selectAssetPage(current, size, query));
    }

    /**
     * 编号占用校验。
     *
     * <p>放在 {@code /{id}} 之前声明，避免 {@code check-code} 被当成 id 解析。</p>
     */
    @GetMapping("/check-code")
    @Operation(summary = "校验资产编号在项目内是否可用")
    @PreAuthorize("hasAuthority('aid:asset:list')")
    public ResponseEntity<Boolean> checkCode(
            @Parameter(description = "所属项目ID") @RequestParam Long projectId,
            @Parameter(description = "资产编号") @RequestParam String assetCode,
            @Parameter(description = "编辑时排除自身ID") @RequestParam(required = false) Long excludeId) {
        return ResponseEntity.ok(assetService.checkCodeUnique(projectId, assetCode, excludeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询资产")
    @PreAuthorize("hasAuthority('aid:asset:query')")
    public ResponseEntity<AidAssetVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.selectAssetById(id));
    }

    @PostMapping
    @Operation(summary = "新增资产")
    @PreAuthorize("hasAuthority('aid:asset:add')")
    @Log(title = "资产库", businessType = BusinessType.INSERT)
    @Idempotent(key = "#dto.projectId + ':' + #dto.assetCode", expire = 30, message = "资产正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody AidAssetDTO dto) {
        return ResponseEntity.ok(assetService.insertAsset(dto));
    }

    @PutMapping
    @Operation(summary = "修改资产")
    @PreAuthorize("hasAuthority('aid:asset:edit')")
    @Log(title = "资产库", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#dto.id", expire = 30, message = "资产正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody AidAssetDTO dto) {
        assetService.updateAsset(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除资产")
    @PreAuthorize("hasAuthority('aid:asset:remove')")
    @Log(title = "资产库", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        assetService.deleteAssets(ids);
        return ResponseEntity.ok().build();
    }

    /** M1 交付物：上传参考图，返回可回显的 URL，前端写入 referenceUrl。 */
    @PostMapping("/upload")
    @Operation(summary = "上传资产参考图")
    @PreAuthorize("hasAuthority('aid:asset:add')")
    @Log(title = "资产库", businessType = BusinessType.INSERT)
    public ResponseEntity<String> upload(@Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(storageService.store(file));
    }

    /**
     * 读取已上传的参考图。
     * <p>鉴权走网关 {@code easywing.gateway.jwt.ignore-paths}（浏览器 {@code <img>} 不带 Authorization 头）。
     * 文件名是 UUID、且必须命中存储目录才返回，因此不能被遍历；
     * 服务直连时仍由 {@code @PreAuthorize} 兜住权限。</p>
     */
    @GetMapping("/files/{fileName}")
    @Operation(summary = "读取资产参考图")
    @PreAuthorize("hasAuthority('aid:asset:query')")
    public ResponseEntity<org.springframework.core.io.Resource> file(
            @Parameter(description = "文件名") @PathVariable String fileName) throws java.net.MalformedURLException {
        java.nio.file.Path path = storageService.resolve(fileName);
        if (!java.nio.file.Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.FileUrlResource(path.toUri().toURL());
        String contentType = resolveContentType(fileName);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }

    private static String resolveContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
