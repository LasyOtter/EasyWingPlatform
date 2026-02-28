package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.query.SysRoleQuery;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysRoleService;
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
@RequestMapping("/api/system/roles")
@Tag(name = "角色管理")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    @Operation(summary = "分页查询角色列表")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ResponseEntity<Page<SysRoleVO>> list(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size, SysRoleQuery query) {
        return ResponseEntity.ok(roleService.selectRolePage(new Page<>(current, size), query));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有角色")
    public ResponseEntity<List<SysRoleVO>> listAll() {
        return ResponseEntity.ok(roleService.selectRoleAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询角色")
    @PreAuthorize("hasAuthority('system:role:query')")
    public ResponseEntity<SysRoleVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.selectRoleById(id));
    }

    @PostMapping
    @Operation(summary = "新增角色")
    @PreAuthorize("hasAuthority('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#roleDTO.roleCode", expire = 30, message = "该角色正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody SysRoleDTO roleDTO) {
        return ResponseEntity.ok(roleService.insertRole(roleDTO));
    }

    @PutMapping
    @Operation(summary = "修改角色")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#roleDTO.id", expire = 30, message = "该角色正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody SysRoleDTO roleDTO) {
        roleService.updateRole(roleDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除角色")
    @PreAuthorize("hasAuthority('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        roleService.deleteRoleByIds(ids);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/menus")
    @Operation(summary = "分配角色菜单权限")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @Idempotent(key = "#id", expire = 30, message = "权限分配操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> authMenu(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.authRoleMenu(id, menuIds);
        return ResponseEntity.ok().build();
    }
}
