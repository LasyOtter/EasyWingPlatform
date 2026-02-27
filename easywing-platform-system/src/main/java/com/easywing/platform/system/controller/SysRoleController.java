package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.domain.query.SysRoleQuery;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysRoleService;
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
    @PreAuthorize("hasAuthority('system:role:list')")
    public ResponseEntity<Page<SysRoleVO>> list(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size, SysRoleQuery query) {
        return ResponseEntity.ok(roleService.selectRolePage(new Page<>(current, size), query));
    }

    @GetMapping("/all")
    public ResponseEntity<List<SysRoleVO>> listAll() {
        return ResponseEntity.ok(roleService.selectRoleAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:query')")
    public ResponseEntity<SysRoleVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.selectRoleById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysRoleDTO roleDTO) {
        return ResponseEntity.ok(roleService.insertRole(roleDTO));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysRoleDTO roleDTO) {
        roleService.updateRole(roleDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        roleService.deleteRoleByIds(ids);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    public ResponseEntity<Void> authMenu(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.authRoleMenu(id, menuIds);
        return ResponseEntity.ok().build();
    }
}
