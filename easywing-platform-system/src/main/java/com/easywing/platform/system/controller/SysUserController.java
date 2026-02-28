package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysUserService;
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
    @Idempotent(key = "#userDTO.username", expire = 30, message = "该用户正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody SysUserDTO userDTO) {
        // 预校验用户名唯一性（提前返回，减少幂等键占用时间）
        if (!userService.checkUsernameUnique(userDTO.getUsername())) {
            throw new BizException(ErrorCode.USERNAME_EXISTS, "用户名已存在");
        }
        return ResponseEntity.ok(userService.insertUser(userDTO));
    }

    @PutMapping
    @Operation(summary = "修改用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#userDTO.id", expire = 30, message = "该用户正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody SysUserDTO userDTO) {
        if (userDTO.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户ID不能为空");
        }
        userService.updateUser(userDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('system:user:remove')")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "T(java.util.Objects).hash(#ids)", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        userService.deleteUserByIds(ids);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改用户状态")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#id + ':' + #status", expire = 30, message = "状态修改操作正在执行中")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#id", expire = 60, message = "密码重置操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        userService.resetPassword(id, password);
        return ResponseEntity.ok().build();
    }
}
