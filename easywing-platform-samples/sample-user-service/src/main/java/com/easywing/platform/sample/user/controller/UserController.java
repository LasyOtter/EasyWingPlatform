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
package com.easywing.platform.sample.user.controller;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户控制器
 * <p>
 * 示例RESTful API控制器，展示RFC 9457错误处理和OAuth2安全
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户增删改查接口")
public class UserController {

    private final Map<Long, User> userStore = new ConcurrentHashMap<>();

    public UserController() {
        userStore.put(1L, new User(1L, "admin", "admin@easywing.io", "管理员"));
        userStore.put(2L, new User(2L, "user", "user@easywing.io", "普通用户"));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "根据JWT令牌获取当前登录用户信息")
    public ResponseEntity<UserInfo> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        
        UserInfo userInfo = new UserInfo(
                jwt.getSubject(),
                username,
                email,
                jwt.getClaimAsStringList("roles")
        );
        
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户", description = "根据用户ID获取用户详细信息")
    public ResponseEntity<User> getUserById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        User user = userStore.get(id);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + id);
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @Operation(summary = "获取用户列表", description = "获取所有用户列表")
    public ResponseEntity<Iterable<User>> listUsers() {
        return ResponseEntity.ok(userStore.values());
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户")
    public ResponseEntity<User> createUser(@RequestBody UserCreateRequest request) {
        long id = userStore.size() + 1L;
        User user = new User(id, request.username(), request.email(), request.nickname());
        userStore.put(id, user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "更新用户信息")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateRequest request) {
        User existingUser = userStore.get(id);
        if (existingUser == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + id);
        }
        
        User updatedUser = new User(
                id,
                existingUser.username(),
                request.email() != null ? request.email() : existingUser.email(),
                request.nickname() != null ? request.nickname() : existingUser.nickname()
        );
        
        userStore.put(id, updatedUser);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User removed = userStore.remove(id);
        if (removed == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 用户信息
     */
    public record User(Long id, String username, String email, String nickname) {
    }

    /**
     * 用户信息（JWT）
     */
    public record UserInfo(String userId, String username, String email, java.util.List<String> roles) {
    }

    /**
     * 创建用户请求
     */
    public record UserCreateRequest(String username, String email, String nickname) {
    }

    /**
     * 更新用户请求
     */
    public record UserUpdateRequest(String email, String nickname) {
    }
}
