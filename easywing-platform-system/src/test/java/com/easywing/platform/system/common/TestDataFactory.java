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
package com.easywing.platform.system.common;

import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据工厂
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class TestDataFactory {

    public static final Long DEFAULT_USER_ID = 1L;
    public static final String DEFAULT_USERNAME = "testuser";
    public static final String DEFAULT_NICKNAME = "Test User";
    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_PHONE = "13800138000";
    public static final Long DEFAULT_DEPT_ID = 1L;

    private TestDataFactory() {}

    /**
     * 创建默认用户实体
     */
    public static SysUser createUser() {
        SysUser user = new SysUser();
        user.setId(DEFAULT_USER_ID);
        user.setUsername(DEFAULT_USERNAME);
        user.setNickname(DEFAULT_NICKNAME);
        user.setEmail(DEFAULT_EMAIL);
        user.setPhone(DEFAULT_PHONE);
        user.setDeptId(DEFAULT_DEPT_ID);
        user.setStatus(0);
        user.setPassword("encoded_password");
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    /**
     * 创建指定ID的用户实体
     */
    public static SysUser createUser(Long userId, String username) {
        SysUser user = createUser();
        user.setId(userId);
        user.setUsername(username);
        return user;
    }

    /**
     * 创建默认用户DTO
     */
    public static SysUserDTO createUserDTO() {
        SysUserDTO dto = new SysUserDTO();
        dto.setId(DEFAULT_USER_ID);
        dto.setUsername(DEFAULT_USERNAME);
        dto.setNickname(DEFAULT_NICKNAME);
        dto.setEmail(DEFAULT_EMAIL);
        dto.setPhone(DEFAULT_PHONE);
        dto.setDeptId(DEFAULT_DEPT_ID);
        dto.setStatus(0);
        return dto;
    }

    /**
     * 创建用于新增的用户DTO（无ID）
     */
    public static SysUserDTO createNewUserDTO(String username) {
        SysUserDTO dto = new SysUserDTO();
        dto.setUsername(username);
        dto.setNickname("New " + username);
        dto.setEmail(username + "@example.com");
        dto.setPhone("13900139000");
        dto.setDeptId(DEFAULT_DEPT_ID);
        dto.setStatus(0);
        return dto;
    }

    /**
     * 创建默认用户VO
     */
    public static SysUserVO createUserVO() {
        SysUserVO vo = new SysUserVO();
        vo.setId(DEFAULT_USER_ID);
        vo.setUsername(DEFAULT_USERNAME);
        vo.setNickname(DEFAULT_NICKNAME);
        vo.setEmail(DEFAULT_EMAIL);
        vo.setPhone(DEFAULT_PHONE);
        vo.setDeptId(DEFAULT_DEPT_ID);
        vo.setStatus(0);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 创建指定ID的用户VO
     */
    public static SysUserVO createUserVO(Long userId, String username) {
        SysUserVO vo = createUserVO();
        vo.setId(userId);
        vo.setUsername(username);
        return vo;
    }

    /**
     * 创建用户查询条件
     */
    public static SysUserQuery createUserQuery() {
        SysUserQuery query = new SysUserQuery();
        query.setUsername("test");
        query.setDeptId(DEFAULT_DEPT_ID);
        query.setStatus(0);
        return query;
    }

    /**
     * 创建用户列表
     */
    public static List<SysUser> createUserList(int count) {
        List<SysUser> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(createUser((long) i, "user" + i));
        }
        return users;
    }

    /**
     * 创建用户VO列表
     */
    public static List<SysUserVO> createUserVOList(int count) {
        List<SysUserVO> vos = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            vos.add(createUserVO((long) i, "user" + i));
        }
        return vos;
    }

    /**
     * 创建用户ID列表
     */
    public static List<Long> createUserIdList(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add((long) i);
        }
        return ids;
    }

    /**
     * 强密码
     */
    public static String strongPassword() {
        return "StrongP@ssw0rd";
    }

    /**
     * 弱密码
     */
    public static String weakPassword() {
        return "123456";
    }
}
