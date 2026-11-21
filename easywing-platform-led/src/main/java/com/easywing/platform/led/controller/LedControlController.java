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

import com.easywing.platform.led.domain.vo.LedDeployResultVO;
import com.easywing.platform.led.domain.vo.LedStatusVO;
import com.easywing.platform.led.service.LedControlService;
import com.easywing.platform.web.version.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LED 运控入口：下发、切节目、开关屏、亮度、刷新、状态。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/led")
@Tag(name = "LED运控")
@ApiVersion("v1")
@Validated
@RequiredArgsConstructor
public class LedControlController {

    private final LedControlService controlService;

    @GetMapping("/status")
    @Operation(summary = "服务状态")
    public ResponseEntity<LedStatusVO> status() {
        return ResponseEntity.ok(controlService.status());
    }

    @PostMapping("/deploy")
    @Operation(summary = "下发全部屏")
    @PreAuthorize("hasAuthority('led:screen:deploy')")
    public ResponseEntity<LedDeployResultVO> deployAll() {
        return ResponseEntity.ok(controlService.deployAll());
    }

    @PostMapping("/deploy/{screenId}")
    @Operation(summary = "下发指定屏")
    @PreAuthorize("hasAuthority('led:screen:deploy')")
    public ResponseEntity<LedDeployResultVO> deployScreen(
            @RequestParam(defaultValue = "false") boolean refresh,
            @org.springframework.web.bind.annotation.PathVariable String screenId) {
        return ResponseEntity.ok(controlService.deployScreen(screenId));
    }

    @PostMapping("/deploy/{screenId}/program/{programNo}")
    @Operation(summary = "下发指定屏的指定节目")
    @PreAuthorize("hasAuthority('led:screen:deploy')")
    public ResponseEntity<LedDeployResultVO> deployProgram(
            @org.springframework.web.bind.annotation.PathVariable String screenId,
            @org.springframework.web.bind.annotation.PathVariable int programNo) {
        return ResponseEntity.ok(controlService.deployProgram(screenId, programNo));
    }

    @PostMapping("/{screenId}/switch/{programIndex}")
    @Operation(summary = "切换屏当前播放节目")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> switchProgram(
            @org.springframework.web.bind.annotation.PathVariable String screenId,
            @org.springframework.web.bind.annotation.PathVariable int programIndex) {
        controlService.switchProgram(screenId, programIndex);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{screenId}/online")
    @Operation(summary = "在线检测")
    public ResponseEntity<Boolean> online(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        return ResponseEntity.ok(controlService.online(screenId));
    }

    @PostMapping("/{screenId}/power")
    @Operation(summary = "开关屏")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> power(
            @org.springframework.web.bind.annotation.PathVariable String screenId, @RequestParam boolean on) {
        controlService.power(screenId, on);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/brightness")
    @Operation(summary = "设置亮度(1-16)")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> brightness(
            @org.springframework.web.bind.annotation.PathVariable String screenId,
            @RequestParam @Min(1) @Max(16) int value) {
        controlService.brightness(screenId, value);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/brightness-timer")
    @Operation(summary = "下发定时亮度")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> brightnessTimer(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.brightnessTimer(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/power-timer")
    @Operation(summary = "下发定时开关机")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> powerTimer(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.powerTimer(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/adjust-time")
    @Operation(summary = "校正控制器时间")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> adjustTime(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.adjustTime(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/screen-info")
    @Operation(summary = "设置屏参")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> screenInfo(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.screenInfo(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/scan-line")
    @Operation(summary = "设置扫描参数")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> scanLine(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.scanLineInfo(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{screenId}/search")
    @Operation(summary = "搜索控制器")
    @PreAuthorize("hasAuthority('led:screen:control')")
    public ResponseEntity<Void> search(
            @org.springframework.web.bind.annotation.PathVariable String screenId,
            @RequestParam(required = false) String iniFilePath,
            @RequestParam(defaultValue = "true") boolean broadcast) {
        controlService.searchController(screenId, iniFilePath, broadcast);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh/{screenId}")
    @Operation(summary = "立即刷新一次")
    @PreAuthorize("hasAuthority('led:screen:deploy')")
    public ResponseEntity<Void> refreshNow(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        controlService.refreshNow(screenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh/start")
    @Operation(summary = "启动刷新调度")
    @PreAuthorize("hasAuthority('led:screen:deploy')")
    public ResponseEntity<Void> startRefresh() {
        controlService.startRefresh();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{screenId}/provider")
    @Operation(summary = "查询屏使用的 SDK 实现")
    public ResponseEntity<String> provider(@org.springframework.web.bind.annotation.PathVariable String screenId) {
        return ResponseEntity.ok(controlService.providerOf(screenId));
    }
}
