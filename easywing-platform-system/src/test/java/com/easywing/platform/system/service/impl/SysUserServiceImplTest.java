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
package com.easywing.platform.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.system.common.TestDataFactory;
import com.easywing.platform.system.config.PageProperties;
import com.easywing.platform.system.config.UserProperties;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.mapper.SysUserMapper;
import com.easywing.platform.system.mapper.struct.UserMapper;
import com.easywing.platform.system.metrics.UserMetrics;
import com.easywing.platform.system.service.PasswordHistoryService;
import com.easywing.platform.system.util.PageHelper;
import com.easywing.platform.system.util.PasswordValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SysUserServiceImpl 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private UserMapper userMapperStruct;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private UserProperties userProperties;

    @Mock
    private UserMetrics userMetrics;

    @Mock
    private PageHelper pageHelper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Sample timerSample;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // 配置 UserProperties 默认值
        when(userProperties.getDefaultPassword()).thenReturn("Test@123456");
        when(userProperties.getMinPasswordLength()).thenReturn(8);
    }

    @Test
    @DisplayName("根据ID查询用户-成功")
    void selectUserById_Success() {
        // Given
        Long userId = 1L;
        SysUser user = TestDataFactory.createUser(userId, "testuser");
        SysUserVO userVO = TestDataFactory.createUserVO(userId, "testuser");

        when(userMapper.selectById(userId)).thenReturn(user);
        when(userMapperStruct.toVO(user)).thenReturn(userVO);

        // When
        SysUserVO result = userService.selectUserById(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("根据ID查询用户-不存在")
    void selectUserById_NotFound() {
        // Given
        Long userId = 999L;
        when(userMapper.selectById(userId)).thenReturn(null);

        // When
        SysUserVO result = userService.selectUserById(userId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("根据用户名查询用户-成功")
    void selectUserByUsername_Success() {
        // Given
        String username = "testuser";
        SysUser user = TestDataFactory.createUser(1L, username);

        when(userMapper.selectUserByUsername(username)).thenReturn(user);

        // When
        SysUser result = userService.selectUserByUsername(username);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("分页查询用户-成功")
    void selectUserPage_Success() {
        // Given
        long current = 1L;
        long size = 10L;
        SysUserQuery query = TestDataFactory.createUserQuery();

        Page<SysUser> userPage = new Page<>(current, size, 2);
        userPage.setRecords(TestDataFactory.createUserList(2));
        List<SysUserVO> voList = TestDataFactory.createUserVOList(2);

        when(userMetrics.startUserQuery()).thenReturn(timerSample);
        when(pageHelper.normalizePage(current, size)).thenReturn(new Page<>(current, size));
        when(pageHelper.getCountCache(anyString())).thenReturn(null);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(userPage);
        when(userMapperStruct.toVO(any(SysUser.class)))
                .thenReturn(voList.get(0), voList.get(1));

        // When
        Page<SysUserVO> result = userService.selectUserPage(current, size, query);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("分页查询用户-使用缓存count")
    void selectUserPage_WithCachedCount() {
        // Given
        long current = 1L;
        long size = 10L;
        SysUserQuery query = TestDataFactory.createUserQuery();

        when(userMetrics.startUserQuery()).thenReturn(timerSample);
        when(pageHelper.normalizePage(current, size)).thenReturn(new Page<>(current, size));
        when(pageHelper.getCountCache(anyString())).thenReturn(0L);

        // When
        Page<SysUserVO> result = userService.selectUserPage(current, size, query);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(0);
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("创建用户-成功")
    void insertUser_Success() {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("newuser");

        when(userProperties.getDefaultPassword()).thenReturn("Test@123456");
        when(userMapper.checkPhoneUnique(any(), any())).thenReturn(0);
        when(userMapper.checkEmailUnique(any(), any())).thenReturn(0);
        when(userMapperStruct.toEntity(userDTO)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");
        when(userMapper.insert(user)).thenReturn(1);

        // When
        Long result = userService.insertUser(userDTO);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(userMapper).insert(user);
        verify(passwordEncoder).encode("Test@123456");
    }

    @Test
    @DisplayName("创建用户-手机号已存在")
    void insertUser_PhoneExists() {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        userDTO.setPhone("13800138000");

        when(userMapper.checkPhoneUnique("13800138000", null)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> userService.insertUser(userDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("手机号码已存在");
    }

    @Test
    @DisplayName("创建用户-邮箱已存在")
    void insertUser_EmailExists() {
        // Given
        SysUserDTO userDTO = TestDataFactory.createNewUserDTO("newuser");
        userDTO.setEmail("test@example.com");

        when(userMapper.checkPhoneUnique(any(), any())).thenReturn(0);
        when(userMapper.checkEmailUnique("test@example.com", null)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> userService.insertUser(userDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱已存在");
    }

    @Test
    @DisplayName("更新用户-成功")
    void updateUser_Success() {
        // Given
        SysUserDTO userDTO = TestDataFactory.createUserDTO();
        userDTO.setNickname("Updated Nickname");
        SysUser existingUser = TestDataFactory.createUser(1L, "testuser");

        when(userMapper.selectById(1L)).thenReturn(existingUser);
        when(userMapper.checkPhoneUnique(any(), any())).thenReturn(0);
        when(userMapper.checkEmailUnique(any(), any())).thenReturn(0);
        when(userMapper.updateById(existingUser)).thenReturn(1);

        // When
        int result = userService.updateUser(userDTO);

        // Then
        assertThat(result).isEqualTo(1);
        verify(userMapperStruct).updateEntity(existingUser, userDTO);
    }

    @Test
    @DisplayName("更新用户-用户ID为空")
    void updateUser_NullId() {
        // Given
        SysUserDTO userDTO = new SysUserDTO();
        userDTO.setId(null);

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(userDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户ID不能为空");
    }

    @Test
    @DisplayName("更新用户-用户不存在")
    void updateUser_UserNotFound() {
        // Given
        SysUserDTO userDTO = TestDataFactory.createUserDTO();
        when(userMapper.selectById(1L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(userDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("重置密码-成功")
    void resetPassword_Success() {
        // Given
        Long userId = 1L;
        String newPassword = "NewPass@123";
        String encodedPassword = "encoded_new_password";

        when(passwordValidator.isStrong(newPassword, 8)).thenReturn(true);
        when(passwordHistoryService.isUsedRecently(userId, newPassword)).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
        when(userMapper.resetPassword(userId, encodedPassword)).thenReturn(1);

        // When
        int result = userService.resetPassword(userId, newPassword);

        // Then
        assertThat(result).isEqualTo(1);
        verify(passwordHistoryService).recordPassword(userId, encodedPassword);
    }

    @Test
    @DisplayName("重置密码-密码强度不足")
    void resetPassword_WeakPassword() {
        // Given
        Long userId = 1L;
        String weakPassword = "123456";

        when(passwordValidator.isStrong(weakPassword, 8)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.resetPassword(userId, weakPassword))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码强度不足");
    }

    @Test
    @DisplayName("重置密码-密码近期已使用")
    void resetPassword_PasswordReused() {
        // Given
        Long userId = 1L;
        String reusedPassword = "OldPass@123";

        when(passwordValidator.isStrong(reusedPassword, 8)).thenReturn(true);
        when(passwordHistoryService.isUsedRecently(userId, reusedPassword)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.resetPassword(userId, reusedPassword))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("近期已使用该密码");
    }

    @Test
    @DisplayName("批量删除用户-成功")
    void deleteUserByIds_Success() {
        // Given
        List<Long> userIds = List.of(1L, 2L, 3L);
        when(userMapper.deleteBatchIds(userIds)).thenReturn(3);

        // When
        int result = userService.deleteUserByIds(userIds);

        // Then
        assertThat(result).isEqualTo(3);
        verify(userMapper).deleteBatchIds(userIds);
    }

    @Test
    @DisplayName("批量删除用户-空列表")
    void deleteUserByIds_EmptyList() {
        // Given
        List<Long> userIds = Collections.emptyList();

        // When
        int result = userService.deleteUserByIds(userIds);

        // Then
        assertThat(result).isEqualTo(0);
        verify(userMapper, never()).deleteBatchIds(any());
    }

    @Test
    @DisplayName("更新用户状态-成功")
    void updateStatus_Success() {
        // Given
        Long userId = 1L;
        Integer status = 1;
        when(userMapper.updateStatus(userId, status)).thenReturn(1);

        // When
        int result = userService.updateStatus(userId, status);

        // Then
        assertThat(result).isEqualTo(1);
        verify(userMapper).updateStatus(userId, status);
    }

    @Test
    @DisplayName("检查用户名唯一性-唯一")
    void checkUsernameUnique_True() {
        // Given
        when(userMapper.checkUsernameUnique("uniqueuser")).thenReturn(0);

        // When
        boolean result = userService.checkUsernameUnique("uniqueuser");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查用户名唯一性-已存在")
    void checkUsernameUnique_False() {
        // Given
        when(userMapper.checkUsernameUnique("existinguser")).thenReturn(1);

        // When
        boolean result = userService.checkUsernameUnique("existinguser");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("检查手机号唯一性-唯一")
    void checkPhoneUnique_True() {
        // Given
        when(userMapper.checkPhoneUnique("13800138000", 1L)).thenReturn(0);

        // When
        boolean result = userService.checkPhoneUnique("13800138000", 1L);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查手机号唯一性-已存在")
    void checkPhoneUnique_False() {
        // Given
        when(userMapper.checkPhoneUnique("13800138000", 1L)).thenReturn(1);

        // When
        boolean result = userService.checkPhoneUnique("13800138000", 1L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("检查邮箱唯一性-唯一")
    void checkEmailUnique_True() {
        // Given
        when(userMapper.checkEmailUnique("test@example.com", 1L)).thenReturn(0);

        // When
        boolean result = userService.checkEmailUnique("test@example.com", 1L);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查邮箱唯一性-已存在")
    void checkEmailUnique_False() {
        // Given
        when(userMapper.checkEmailUnique("test@example.com", 1L)).thenReturn(1);

        // When
        boolean result = userService.checkEmailUnique("test@example.com", 1L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("导出用户-成功")
    void exportUsers_Success() {
        // Given
        SysUserQuery query = TestDataFactory.createUserQuery();
        List<SysUser> users = TestDataFactory.createUserList(2);
        List<SysUserVO> voList = TestDataFactory.createUserVOList(2);

        when(userMapper.selectList(any())).thenReturn(users);
        when(userMapperStruct.toVOList(users)).thenReturn(voList);

        // When
        List<SysUserVO> result = userService.exportUsers(query);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("获取当前用户信息-无用户ID")
    void getCurrentUserInfo_NoUserId() {
        // When
        SysUserVO result = userService.getCurrentUserInfo();

        // Then
        assertThat(result).isNull();
    }
}
