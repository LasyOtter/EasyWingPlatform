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
package com.easywing.platform.system.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.common.TestDataFactory;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.service.SysUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SysUserController 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@WebMvcTest(SysUserController.class)
class SysUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SysUserService userService;

    @Test
    @DisplayName("分页查询用户列表-成功")
    void list_Success() throws Exception {
        // Given
        Page<SysUserVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(TestDataFactory.createUserVO()));

        when(userService.selectUserPage(eq(1L), eq(10L), any())).thenReturn(page);

        // When & Then
        mockMvc.perform(
                        get("/api/system/users")
                                .param("current", "1")
                                .param("size", "10")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("分页查询用户列表-无权限")
    void list_AccessDenied() throws Exception {
        mockMvc.perform(
                        get("/api/system/users")
                                .param("current", "1")
                                .param("size", "10")
                                .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("根据ID查询用户-成功")
    void getInfo_Success() throws Exception {
        // Given
        SysUserVO userVO = TestDataFactory.createUserVO(1L, "testuser");
        when(userService.selectUserById(1L)).thenReturn(userVO);

        // When & Then
        mockMvc.perform(
                        get("/api/system/users/1")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:query"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("根据ID查询用户-无权限")
    void getInfo_AccessDenied() throws Exception {
        mockMvc.perform(get("/api/system/users/1").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("获取当前用户信息-成功")
    void getCurrentUser_Success() throws Exception {
        // Given
        SysUserVO userVO = TestDataFactory.createUserVO();
        when(userService.getCurrentUserInfo()).thenReturn(userVO);

        // When & Then
        mockMvc.perform(get("/api/system/users/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(TestDataFactory.DEFAULT_USERNAME));
    }

    @Test
    @DisplayName("创建用户-成功")
    void add_Success() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        when(userService.checkUsernameUnique("newuser")).thenReturn(true);
        when(userService.insertUser(any())).thenReturn(1L);

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("创建用户-用户名已存在")
    void add_UsernameExists() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("existinguser");
        when(userService.checkUsernameUnique("existinguser")).thenReturn(false);

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("创建用户-参数校验失败-用户名为空")
    void add_ValidationFailed_UsernameEmpty() throws Exception {
        // Given
        SysUserDTO userDTO = new SysUserDTO();
        userDTO.setNickname("Test User");

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("创建用户-参数校验失败-用户名太短")
    void add_ValidationFailed_UsernameTooShort() throws Exception {
        // Given
        SysUserDTO userDTO = new SysUserDTO();
        userDTO.setUsername("a");
        userDTO.setNickname("Test User");

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("创建用户-参数校验失败-邮箱格式错误")
    void add_ValidationFailed_InvalidEmail() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        userDTO.setEmail("invalid-email");

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("创建用户-参数校验失败-手机号格式错误")
    void add_ValidationFailed_InvalidPhone() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        userDTO.setPhone("12345678900");

        // When & Then
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:add"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("创建用户-无权限")
    void add_AccessDenied() throws Exception {
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");

        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("更新用户-成功")
    void edit_Success() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createUserDTO();
        userDTO.setNickname("Updated Name");

        when(userService.updateUser(any())).thenReturn(1);

        // When & Then
        mockMvc.perform(
                        put("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:edit"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("更新用户-用户ID为空")
    void edit_NullId() throws Exception {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("testuser");
        userDTO.setId(null);

        // When & Then
        mockMvc.perform(
                        put("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:edit"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户ID不能为空"));
    }

    @Test
    @DisplayName("更新用户-无权限")
    void edit_AccessDenied() throws Exception {
        SysUserDTO userDTO = TestDataFactory.createUserDTO();

        mockMvc.perform(
                        put("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userDTO))
                                .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("删除用户-成功")
    void remove_Success() throws Exception {
        when(userService.deleteUserByIds(List.of(1L, 2L, 3L))).thenReturn(3);

        mockMvc.perform(
                        delete("/api/system/users/1,2,3")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:remove"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("删除用户-无权限")
    void remove_AccessDenied() throws Exception {
        mockMvc.perform(delete("/api/system/users/1,2,3").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("修改用户状态-成功")
    void changeStatus_Success() throws Exception {
        when(userService.updateStatus(1L, 0)).thenReturn(1);

        mockMvc.perform(
                        put("/api/system/users/1/status")
                                .param("status", "0")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:edit"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("修改用户状态-无权限")
    void changeStatus_AccessDenied() throws Exception {
        mockMvc.perform(
                        put("/api/system/users/1/status")
                                .param("status", "0")
                                .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("重置密码-成功")
    void resetPassword_Success() throws Exception {
        when(userService.resetPassword(1L, "NewPass@123")).thenReturn(1);

        mockMvc.perform(
                        put("/api/system/users/1/reset-password")
                                .param("password", "NewPass@123")
                                .with(jwt()
                                        .authorities(new SimpleGrantedAuthority("system:user:resetPwd"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("重置密码-无权限")
    void resetPassword_AccessDenied() throws Exception {
        mockMvc.perform(
                        put("/api/system/users/1/reset-password")
                                .param("password", "NewPass@123")
                                .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("重置密码-缺少权限声明")
    void resetPassword_MissingAuthority() throws Exception {
        mockMvc.perform(
                        put("/api/system/users/1/reset-password")
                                .param("password", "NewPass@123")
                                .with(jwt().authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isForbidden());
    }
}
