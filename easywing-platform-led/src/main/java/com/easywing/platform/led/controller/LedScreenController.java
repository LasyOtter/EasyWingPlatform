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
package com.easywing.platform.led.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.led.domain.dto.LedScreenDTO;
import com.easywing.platform.led.domain.vo.LedScreenVO;
import com.easywing.platform.led.service.LedScreenService;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 屏元数据管理。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/led/screens")
@Tag(name = "LED屏管理")
@ApiVersion("v1")
@RequiredArgsConstructor
public class LedScreenController {

    private final LedScreenService screenService;

    @GetMapping
    @PreAuthorize("hasAuthority('led:screen:list')")
    public ResponseEntity<Page<LedScreenVO>> list(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String screenId,
            @RequestParam(required = false) String sdkProvider) {
        return ResponseEntity.ok(screenService.page(new Page<>(current, size), screenId, sdkProvider));
    }

    @GetMapping("/all")
    public ResponseEntity<List<LedScreenVO>> listAll() {
        return ResponseEntity.ok(screenService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('led:screen:query')")
    public ResponseEntity<LedScreenVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(screenService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('led:screen:add')")
    public ResponseEntity<Long> add(@Valid @RequestBody LedScreenDTO dto) {
        return ResponseEntity.ok(screenService.create(dto));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('led:screen:edit')")
    public ResponseEntity<Void> edit(@Valid @RequestBody LedScreenDTO dto) {
        screenService.update(dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('led:screen:remove')")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        screenService.delete(ids);
        return ResponseEntity.ok().build();
    }
}
