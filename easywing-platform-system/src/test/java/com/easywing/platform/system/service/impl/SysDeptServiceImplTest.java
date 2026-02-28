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

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.system.domain.dto.SysDeptDTO;
import com.easywing.platform.system.domain.entity.SysDept;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import com.easywing.platform.system.mapper.SysDeptMapper;
import com.easywing.platform.system.mapper.struct.DeptMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SysDeptServiceImpl 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SysDeptServiceImplTest {

    @Mock
    private SysDeptMapper deptMapper;

    @Mock
    private DeptMapper deptMapperStruct;

    @InjectMocks
    private SysDeptServiceImpl deptService;

    @Test
    @DisplayName("查询部门列表-成功")
    void selectDeptList_Success() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setDeptName("技术部");
        deptDTO.setStatus(0);

        SysDept dept1 = createDept(1L, "技术部", 0L, "0");
        SysDept dept2 = createDept(2L, "研发组", 1L, "0,1");

        SysDeptVO vo1 = createDeptVO(1L, "技术部", 0L);
        SysDeptVO vo2 = createDeptVO(2L, "研发组", 1L);

        when(deptMapper.selectList(any())).thenReturn(List.of(dept1, dept2));
        when(deptMapperStruct.toVOList(any())).thenReturn(List.of(vo1, vo2));

        // When
        List<SysDeptVO> result = deptService.selectDeptList(deptDTO);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("查询部门树列表-成功")
    void selectDeptTreeList_Success() {
        // Given
        SysDept dept1 = createDept(1L, "技术部", 0L, "0");
        SysDept dept2 = createDept(2L, "研发组", 1L, "0,1");
        dept2.setOrderNum(1);

        SysDeptVO vo1 = createDeptVO(1L, "技术部", 0L);
        SysDeptVO vo2 = createDeptVO(2L, "研发组", 1L);
        vo2.setOrderNum(1);

        when(deptMapper.selectList(any())).thenReturn(List.of(dept1, dept2));
        when(deptMapperStruct.toVOList(any())).thenReturn(List.of(vo1, vo2));

        // When
        List<SysDeptVO> result = deptService.selectDeptTreeList();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChildren()).hasSize(1);
        assertThat(result.get(0).getChildren().get(0).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("根据ID查询部门-成功")
    void selectDeptById_Success() {
        // Given
        Long deptId = 1L;
        SysDept dept = createDept(deptId, "技术部", 0L, "0");
        SysDeptVO vo = createDeptVO(deptId, "技术部", 0L);

        when(deptMapper.selectById(deptId)).thenReturn(dept);
        when(deptMapperStruct.toVO(dept)).thenReturn(vo);

        // When
        SysDeptVO result = deptService.selectDeptById(deptId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(deptId);
        assertThat(result.getChildren()).isNotNull();
    }

    @Test
    @DisplayName("根据ID查询部门-不存在")
    void selectDeptById_NotFound() {
        // Given
        Long deptId = 999L;
        when(deptMapper.selectById(deptId)).thenReturn(null);

        // When
        SysDeptVO result = deptService.selectDeptById(deptId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("根据用户ID查询部门-成功")
    void selectDeptByUserId_Success() {
        // Given
        Long userId = 1L;
        SysDept dept = createDept(1L, "技术部", 0L, "0");
        SysDeptVO vo = createDeptVO(1L, "技术部", 0L);

        when(deptMapper.selectDeptByUserId(userId)).thenReturn(dept);
        when(deptMapperStruct.toVO(dept)).thenReturn(vo);

        // When
        SysDeptVO result = deptService.selectDeptByUserId(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDeptName()).isEqualTo("技术部");
    }

    @Test
    @DisplayName("创建部门-成功（根部门）")
    void insertDept_Success_Root() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setDeptName("技术部");
        deptDTO.setParentId(0L);

        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
            SysDept d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        // When
        Long result = deptService.insertDept(deptDTO);

        // Then
        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("创建部门-成功（子部门）")
    void insertDept_Success_Child() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setDeptName("研发组");
        deptDTO.setParentId(1L);

        SysDept parent = createDept(1L, "技术部", 0L, "0");

        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectById(1L)).thenReturn(parent);
        when(deptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
            SysDept d = inv.getArgument(0);
            d.setId(2L);
            return 1;
        });

        // When
        Long result = deptService.insertDept(deptDTO);

        // Then
        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("创建部门-部门名称已存在")
    void insertDept_NameExists() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setDeptName("技术部");
        deptDTO.setParentId(0L);

        when(deptMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> deptService.insertDept(deptDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("部门名称已存在");
    }

    @Test
    @DisplayName("更新部门-成功")
    void updateDept_Success() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setId(1L);
        deptDTO.setDeptName("技术部（新）");
        deptDTO.setParentId(0L);

        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);

        // When
        int result = deptService.updateDept(deptDTO);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("更新部门-部门ID为空")
    void updateDept_NullId() {
        // Given
        SysDeptDTO deptDTO = new SysDeptDTO();
        deptDTO.setId(null);

        // When & Then
        assertThatThrownBy(() -> deptService.updateDept(deptDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("部门ID不能为空");
    }

    @Test
    @DisplayName("删除部门-成功")
    void deleteDeptById_Success() {
        // Given
        Long deptId = 1L;
        when(deptMapper.selectChildrenCountByParentId(deptId)).thenReturn(0);
        when(deptMapper.checkDeptExistUser(deptId)).thenReturn(0);
        when(deptMapper.deleteById(deptId)).thenReturn(1);

        // When
        int result = deptService.deleteDeptById(deptId);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("删除部门-存在子部门")
    void deleteDeptById_HasChildren() {
        // Given
        Long deptId = 1L;
        when(deptMapper.selectChildrenCountByParentId(deptId)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> deptService.deleteDeptById(deptId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("存在子部门");
    }

    @Test
    @DisplayName("删除部门-部门存在用户")
    void deleteDeptById_HasUsers() {
        // Given
        Long deptId = 1L;
        when(deptMapper.selectChildrenCountByParentId(deptId)).thenReturn(0);
        when(deptMapper.checkDeptExistUser(deptId)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> deptService.deleteDeptById(deptId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("部门存在用户");
    }

    @Test
    @DisplayName("检查部门名称唯一性-唯一")
    void checkDeptNameUnique_True() {
        // Given
        when(deptMapper.selectCount(any())).thenReturn(0L);

        // When
        boolean result = deptService.checkDeptNameUnique("技术部", 0L, null);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查部门名称唯一性-已存在")
    void checkDeptNameUnique_False() {
        // Given
        when(deptMapper.selectCount(any())).thenReturn(1L);

        // When
        boolean result = deptService.checkDeptNameUnique("技术部", 0L, null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("是否有子部门-有")
    void hasChildren_True() {
        // Given
        Long deptId = 1L;
        when(deptMapper.selectChildrenCountByParentId(deptId)).thenReturn(2);

        // When
        boolean result = deptService.hasChildren(deptId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("是否有子部门-无")
    void hasChildren_False() {
        // Given
        Long deptId = 1L;
        when(deptMapper.selectChildrenCountByParentId(deptId)).thenReturn(0);

        // When
        boolean result = deptService.hasChildren(deptId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("检查部门是否存在用户-存在")
    void checkDeptExistUser_True() {
        // Given
        Long deptId = 1L;
        when(deptMapper.checkDeptExistUser(deptId)).thenReturn(3);

        // When
        boolean result = deptService.checkDeptExistUser(deptId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查部门是否存在用户-不存在")
    void checkDeptExistUser_False() {
        // Given
        Long deptId = 1L;
        when(deptMapper.checkDeptExistUser(deptId)).thenReturn(0);

        // When
        boolean result = deptService.checkDeptExistUser(deptId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("根据父部门ID查询子部门ID列表")
    void selectDeptIdsByParentId_Success() {
        // Given
        Long deptId = 1L;
        List<Long> ids = List.of(2L, 3L, 4L);
        when(deptMapper.selectDeptIdsByParentId(deptId)).thenReturn(ids);

        // When
        List<Long> result = deptService.selectDeptIdsByParentId(deptId);

        // Then
        assertThat(result).hasSize(3);
    }

    private SysDept createDept(Long id, String name, Long parentId, String ancestors) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setDeptName(name);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setStatus(0);
        dept.setOrderNum(0);
        return dept;
    }

    private SysDeptVO createDeptVO(Long id, String name, Long parentId) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(id);
        vo.setDeptName(name);
        vo.setParentId(parentId);
        vo.setStatus(0);
        vo.setOrderNum(0);
        return vo;
    }
}
