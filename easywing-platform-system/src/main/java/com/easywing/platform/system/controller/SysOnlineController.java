package com.easywing.platform.system.controller;

import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.vo.SysOnlineVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysOnlineService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/online")
@Tag(name = "在线用户管理")
@RequiredArgsConstructor
public class SysOnlineController {

    private final SysOnlineService onlineService;

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:online:list')")
    public ResponseEntity<List<SysOnlineVO>> list(@RequestParam(required = false) String username) {
        return ResponseEntity.ok(onlineService.selectOnlineList(username));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(onlineService.getOnlineCount());
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAuthority('monitor:online:force-logout')")
    @Log(title = "在线用户管理", businessType = BusinessType.FORCE)
    public ResponseEntity<Void> forceLogout(@PathVariable String sessionId) {
        onlineService.forceLogout(sessionId);
        return ResponseEntity.ok().build();
    }
}
