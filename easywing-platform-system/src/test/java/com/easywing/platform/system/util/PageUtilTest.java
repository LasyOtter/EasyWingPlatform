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
package com.easywing.platform.system.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.vo.SysUserVO;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PageUtil 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class PageUtilTest {

    @Test
    @DisplayName("转换分页对象-成功")
    void convert_Success() {
        // Given
        Page<SysUser> source = new Page<>(1, 10, 2);
        SysUser user1 = new SysUser();
        user1.setId(1L);
        user1.setUsername("user1");
        SysUser user2 = new SysUser();
        user2.setId(2L);
        user2.setUsername("user2");
        source.setRecords(List.of(user1, user2));

        Function<SysUser, SysUserVO> converter = user -> {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            return vo;
        };

        // When
        Page<SysUserVO> result = PageUtil.convert(source, converter);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("user1");
    }

    @Test
    @DisplayName("转换分页对象-空记录")
    void convert_EmptyRecords() {
        // Given
        Page<SysUser> source = new Page<>(1, 10, 0);
        source.setRecords(Collections.emptyList());

        Function<SysUser, SysUserVO> converter = user -> {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            return vo;
        };

        // When
        Page<SysUserVO> result = PageUtil.convert(source, converter);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("转换列表-成功")
    void convertList_Success() {
        // Given
        SysUser user1 = new SysUser();
        user1.setId(1L);
        user1.setUsername("user1");
        SysUser user2 = new SysUser();
        user2.setId(2L);
        user2.setUsername("user2");
        List<SysUser> source = List.of(user1, user2);

        Function<SysUser, SysUserVO> converter = user -> {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            return vo;
        };

        // When
        List<SysUserVO> result = PageUtil.convertList(source, converter);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getUsername()).isEqualTo("user2");
    }

    @Test
    @DisplayName("转换列表-空列表")
    void convertList_EmptyList() {
        // Given
        List<SysUser> source = Collections.emptyList();
        Function<SysUser, SysUserVO> converter = user -> new SysUserVO();

        // When
        List<SysUserVO> result = PageUtil.convertList(source, converter);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("转换列表-null列表")
    void convertList_NullList() {
        // Given
        Function<SysUser, SysUserVO> converter = user -> new SysUserVO();

        // When
        List<SysUserVO> result = PageUtil.convertList(null, converter);

        // Then
        assertThat(result).isEmpty();
    }
}
