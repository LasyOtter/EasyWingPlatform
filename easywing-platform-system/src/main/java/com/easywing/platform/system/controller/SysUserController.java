package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/users")
@Tag(name = "用户管理")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping
    @Operation(summary = "分页查询用户列表")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ResponseEntity<Page<SysUserVO>> list(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size, SysUserQuery query) {
        return ResponseEntity.ok(userService.selectUserPage(new Page<>(current, size), query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询用户")
    @PreAuthorize("hasAuthority('system:user:query')")
    public ResponseEntity<SysUserVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(userService.selectUserById(id));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ResponseEntity<SysUserVO> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUserInfo());
    }

    @PostMapping
    @Operation(summary = "新增用户")
    @PreAuthorize("hasAuthority('system:user:add')")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysUserDTO userDTO) {
        return ResponseEntity.ok(userService.insertUser(userDTO));
    }

    @PutMapping
    @Operation(summary = "修改用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysUserDTO userDTO) {
        userService.updateUser(userDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('system:user:remove')")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        userService.deleteUserByIds(ids);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改用户状态")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        userService.resetPassword(id, password);
        return ResponseEntity.ok().build();
    }
}
