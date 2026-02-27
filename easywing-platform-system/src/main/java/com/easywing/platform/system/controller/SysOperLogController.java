package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.entity.SysOperLog;
import com.easywing.platform.system.domain.query.SysOperLogQuery;
import com.easywing.platform.system.domain.vo.SysOperLogVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysOperLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/oper-logs")
@Tag(name = "操作日志管理")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:operlog:list')")
    public ResponseEntity<Page<SysOperLogVO>> list(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size, SysOperLogQuery query) {
        return ResponseEntity.ok(operLogService.selectOperLogPage(new Page<>(current, size), query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:operlog:query')")
    public ResponseEntity<SysOperLogVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(operLogService.selectOperLogById(id));
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('monitor:operlog:remove')")
    @Log(title = "操作日志管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        operLogService.deleteOperLogByIds(ids);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clean")
    @PreAuthorize("hasAuthority('monitor:operlog:remove')")
    @Log(title = "操作日志管理", businessType = BusinessType.CLEAN)
    public ResponseEntity<Void> clean() {
        operLogService.cleanOperLog();
        return ResponseEntity.ok().build();
    }
}
