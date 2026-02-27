package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.entity.SysLoginLog;
import com.easywing.platform.system.domain.query.SysLoginLogQuery;
import com.easywing.platform.system.domain.vo.SysLoginLogVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysLoginLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/login-logs")
@Tag(name = "登录日志管理")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:logininfor:list')")
    public ResponseEntity<Page<SysLoginLogVO>> list(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size, SysLoginLogQuery query) {
        return ResponseEntity.ok(loginLogService.selectLoginLogPage(new Page<>(current, size), query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:logininfor:query')")
    public ResponseEntity<SysLoginLogVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(loginLogService.selectLoginLogById(id));
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('monitor:logininfor:remove')")
    @Log(title = "登录日志管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        loginLogService.deleteLoginLogByIds(ids);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clean")
    @PreAuthorize("hasAuthority('monitor:logininfor:remove')")
    @Log(title = "登录日志管理", businessType = BusinessType.CLEAN)
    public ResponseEntity<Void> clean() {
        loginLogService.cleanLoginLog();
        return ResponseEntity.ok().build();
    }
}
