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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.system.domain.dto.SysPostDTO;
import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import com.easywing.platform.system.mapper.SysPostMapper;
import com.easywing.platform.system.mapper.struct.PostMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SysPostServiceImpl 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SysPostServiceImplTest {

    @Mock
    private SysPostMapper postMapper;

    @Mock
    private PostMapper postMapperStruct;

    @InjectMocks
    private SysPostServiceImpl postService;

    @Test
    @DisplayName("查询全部岗位-成功")
    void selectPostAll_success() {
        SysPost post = createPost(1L, "P001", "研发", 0);
        SysPostVO vo = createPostVO(1L, "P001", "研发");
        when(postMapper.selectList(any())).thenReturn(List.of(post));
        when(postMapperStruct.toVOList(any())).thenReturn(List.of(vo));

        List<SysPostVO> result = postService.selectPostAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPostCode()).isEqualTo("P001");
    }

    @Test
    @DisplayName("按ID查询岗位-存在")
    void selectPostById_found() {
        when(postMapper.selectById(1L)).thenReturn(createPost(1L, "P001", "研发", 0));
        when(postMapperStruct.toVO(any())).thenReturn(createPostVO(1L, "P001", "研发"));

        SysPostVO result = postService.selectPostById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("按ID查询岗位-不存在返回null")
    void selectPostById_notFound() {
        when(postMapper.selectById(9L)).thenReturn(null);

        assertThat(postService.selectPostById(9L)).isNull();
    }

    @Test
    @DisplayName("新增岗位-编码重复抛异常")
    void insertPost_duplicateCode() {
        when(postMapper.selectCount(any())).thenReturn(1L);

        SysPostDTO dto = new SysPostDTO();
        dto.setPostCode("P001");

        assertThatThrownBy(() -> postService.insertPost(dto)).isInstanceOf(BizException.class);
        verify(postMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增岗位-成功")
    void insertPost_success() {
        when(postMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
                    SysPost post = invocation.getArgument(0);
                    post.setId(10L);
                    return 1;
                })
                .when(postMapper)
                .insert(any(SysPost.class));

        SysPostDTO dto = new SysPostDTO();
        dto.setPostCode("P001");
        dto.setPostName("研发");
        dto.setStatus(0);

        Long id = postService.insertPost(dto);

        assertThat(id).isEqualTo(10L);
        verify(postMapper).insert(any(SysPost.class));
    }

    @Test
    @DisplayName("更新岗位-ID为空抛异常")
    void updatePost_missingId() {
        SysPostDTO dto = new SysPostDTO();
        dto.setPostCode("P001");

        assertThatThrownBy(() -> postService.updatePost(dto)).isInstanceOf(BizException.class);
        verify(postMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除岗位-空列表直接返回0")
    void deletePostByIds_empty() {
        assertThat(postService.deletePostByIds(List.of())).isZero();
        verify(postMapper, never()).deleteBatchIds(any());
    }

    @Test
    @DisplayName("岗位编码唯一性校验")
    void checkPostCodeUnique() {
        when(postMapper.selectCount(any())).thenReturn(0L);
        assertThat(postService.checkPostCodeUnique("P001", null)).isTrue();

        when(postMapper.selectCount(any())).thenReturn(1L);
        assertThat(postService.checkPostCodeUnique("P001", null)).isFalse();
    }

    private SysPost createPost(Long id, String postCode, String postName, Integer status) {
        SysPost post = new SysPost();
        post.setId(id);
        post.setPostCode(postCode);
        post.setPostName(postName);
        post.setStatus(status);
        return post;
    }

    private SysPostVO createPostVO(Long id, String postCode, String postName) {
        SysPostVO vo = new SysPostVO();
        vo.setId(id);
        vo.setPostCode(postCode);
        vo.setPostName(postName);
        return vo;
    }
}
