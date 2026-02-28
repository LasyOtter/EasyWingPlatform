package com.easywing.platform.system.controller;

import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysMenuDTO;
import com.easywing.platform.system.domain.vo.RouterVO;
import com.easywing.platform.system.domain.vo.SysMenuVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysMenuService;
import com.easywing.platform.system.util.SecurityUtils;
import com.easywing.platform.web.idempotent.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@Tag(name = "菜单管理")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping
    @Operation(summary = "查询菜单列表")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ResponseEntity<List<SysMenuVO>> list(SysMenuDTO menuDTO) {
        return ResponseEntity.ok(menuService.selectMenuList(menuDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询菜单")
    @PreAuthorize("hasAuthority('system:menu:query')")
    public ResponseEntity<SysMenuVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.selectMenuById(id));
    }

    @GetMapping("/routers")
    @Operation(summary = "获取路由菜单")
    public ResponseEntity<List<RouterVO>> getRouters() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.ok(List.of());
        List<SysMenuVO> menus = menuService.selectMenusByUserId(Long.parseLong(userId));
        return ResponseEntity.ok(menuService.buildRouters(menus));
    }

    @PostMapping
    @Operation(summary = "新增菜单")
    @PreAuthorize("hasAuthority('system:menu:add')")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#menuDTO.menuName + ':' + #menuDTO.parentId", expire = 30, message = "该菜单正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody SysMenuDTO menuDTO) {
        return ResponseEntity.ok(menuService.insertMenu(menuDTO));
    }

    @PutMapping
    @Operation(summary = "修改菜单")
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#menuDTO.id", expire = 30, message = "该菜单正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody SysMenuDTO menuDTO) {
        menuService.updateMenu(menuDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单")
    @PreAuthorize("hasAuthority('system:menu:remove')")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "#id", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        menuService.deleteMenuById(id);
        return ResponseEntity.ok().build();
    }
}
