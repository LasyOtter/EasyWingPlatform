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
import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.domain.query.SysRoleQuery;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import com.easywing.platform.system.enums.DataScope;
import com.easywing.platform.system.mapper.SysRoleMapper;
import com.easywing.platform.system.mapper.struct.RoleMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SysRoleServiceImpl 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplTest {

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private RoleMapper roleMapperStruct;

    @InjectMocks
    private SysRoleServiceImpl roleService;

    @Test
    @DisplayName("分页查询角色-成功")
    void selectRolePage_Success() {
        // Given
        Page<SysRole> page = new Page<>(1, 10);
        SysRoleQuery query = new SysRoleQuery();
        query.setRoleName("admin");

        Page<SysRole> rolePage = new Page<>(1, 10, 1);
        SysRole role = createRole(1L, "admin", "ROLE_ADMIN");
        rolePage.setRecords(List.of(role));

        SysRoleVO roleVO = createRoleVO(1L, "admin", "ROLE_ADMIN");

        when(roleMapper.selectPage(any(), any())).thenReturn(rolePage);
        when(roleMapperStruct.toVO(role)).thenReturn(roleVO);

        // When
        Page<SysRoleVO> result = roleService.selectRolePage(page, query);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("查询所有角色-成功")
    void selectRoleAll_Success() {
        // Given
        SysRole role = createRole(1L, "admin", "ROLE_ADMIN");
        role.setDataScope(DataScope.ALL.getCode());

        SysRoleVO roleVO = createRoleVO(1L, "admin", "ROLE_ADMIN");
        roleVO.setDataScope(DataScope.ALL.getCode());

        when(roleMapper.selectList(any())).thenReturn(List.of(role));
        when(roleMapperStruct.toVOList(any())).thenReturn(List.of(roleVO));

        // When
        List<SysRoleVO> result = roleService.selectRoleAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDataScopeDesc()).isEqualTo(DataScope.ALL.getDescription());
    }

    @Test
    @DisplayName("根据ID查询角色-成功")
    void selectRoleById_Success() {
        // Given
        Long roleId = 1L;
        SysRole role = createRole(roleId, "admin", "ROLE_ADMIN");
        role.setDataScope(DataScope.CUSTOM.getCode());

        SysRoleVO roleVO = createRoleVO(roleId, "admin", "ROLE_ADMIN");
        roleVO.setDataScope(DataScope.CUSTOM.getCode());

        when(roleMapper.selectById(roleId)).thenReturn(role);
        when(roleMapperStruct.toVO(role)).thenReturn(roleVO);

        // When
        SysRoleVO result = roleService.selectRoleById(roleId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(roleId);
        assertThat(result.getDataScopeDesc()).isEqualTo(DataScope.CUSTOM.getDescription());
    }

    @Test
    @DisplayName("根据ID查询角色-不存在")
    void selectRoleById_NotFound() {
        // Given
        Long roleId = 999L;
        when(roleMapper.selectById(roleId)).thenReturn(null);

        // When
        SysRoleVO result = roleService.selectRoleById(roleId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("根据用户ID查询角色-成功")
    void selectRolesByUserId_Success() {
        // Given
        Long userId = 1L;
        SysRole role = createRole(1L, "admin", "ROLE_ADMIN");
        role.setDataScope(DataScope.DEPT_ONLY.getCode());

        SysRoleVO roleVO = createRoleVO(1L, "admin", "ROLE_ADMIN");
        roleVO.setDataScope(DataScope.DEPT_ONLY.getCode());

        when(roleMapper.selectRolesByUserId(userId)).thenReturn(List.of(role));
        when(roleMapperStruct.toVOList(any())).thenReturn(List.of(roleVO));

        // When
        List<SysRoleVO> result = roleService.selectRolesByUserId(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDataScopeDesc()).isEqualTo(DataScope.DEPT_ONLY.getDescription());
    }

    @Test
    @DisplayName("创建角色-成功")
    void insertRole_Success() {
        // Given
        SysRoleDTO roleDTO = new SysRoleDTO();
        roleDTO.setRoleName("newRole");
        roleDTO.setRoleCode("ROLE_NEW");

        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleName("newRole");

        when(roleMapper.checkRoleNameUnique("newRole", null)).thenReturn(0);
        when(roleMapper.checkRoleCodeUnique("ROLE_NEW", null)).thenReturn(0);
        when(roleMapperStruct.toEntity(roleDTO)).thenReturn(role);
        when(roleMapper.insert(role)).thenReturn(1);

        // When
        Long result = roleService.insertRole(roleDTO);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(roleMapper).insert(role);
    }

    @Test
    @DisplayName("创建角色-角色名称已存在")
    void insertRole_NameExists() {
        // Given
        SysRoleDTO roleDTO = new SysRoleDTO();
        roleDTO.setRoleName("existingRole");
        roleDTO.setRoleCode("ROLE_NEW");

        when(roleMapper.checkRoleNameUnique("existingRole", null)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> roleService.insertRole(roleDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色名称已存在");
    }

    @Test
    @DisplayName("创建角色-角色编码已存在")
    void insertRole_CodeExists() {
        // Given
        SysRoleDTO roleDTO = new SysRoleDTO();
        roleDTO.setRoleName("newRole");
        roleDTO.setRoleCode("ROLE_EXISTING");

        when(roleMapper.checkRoleNameUnique("newRole", null)).thenReturn(0);
        when(roleMapper.checkRoleCodeUnique("ROLE_EXISTING", null)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> roleService.insertRole(roleDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色权限字符已存在");
    }

    @Test
    @DisplayName("更新角色-成功")
    void updateRole_Success() {
        // Given
        SysRoleDTO roleDTO = new SysRoleDTO();
        roleDTO.setId(1L);
        roleDTO.setRoleName("updatedRole");
        roleDTO.setRoleCode("ROLE_UPDATED");

        SysRole role = createRole(1L, "updatedRole", "ROLE_UPDATED");

        when(roleMapper.checkRoleNameUnique("updatedRole", 1L)).thenReturn(0);
        when(roleMapper.checkRoleCodeUnique("ROLE_UPDATED", 1L)).thenReturn(0);
        when(roleMapperStruct.toEntity(roleDTO)).thenReturn(role);
        when(roleMapper.updateById(role)).thenReturn(1);

        // When
        int result = roleService.updateRole(roleDTO);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("更新角色-角色ID为空")
    void updateRole_NullId() {
        // Given
        SysRoleDTO roleDTO = new SysRoleDTO();
        roleDTO.setId(null);

        // When & Then
        assertThatThrownBy(() -> roleService.updateRole(roleDTO))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色ID不能为空");
    }

    @Test
    @DisplayName("批量删除角色-成功")
    void deleteRoleByIds_Success() {
        // Given
        List<Long> roleIds = List.of(1L, 2L, 3L);
        when(roleMapper.deleteBatchIds(roleIds)).thenReturn(3);

        // When
        int result = roleService.deleteRoleByIds(roleIds);

        // Then
        assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("批量删除角色-空列表")
    void deleteRoleByIds_EmptyList() {
        // Given
        List<Long> roleIds = Collections.emptyList();

        // When
        int result = roleService.deleteRoleByIds(roleIds);

        // Then
        assertThat(result).isEqualTo(0);
        verify(roleMapper, never()).deleteBatchIds(any());
    }

    @Test
    @DisplayName("更新角色状态-成功")
    void updateStatus_Success() {
        // Given
        Long roleId = 1L;
        Integer status = 0;
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);

        // When
        int result = roleService.updateStatus(roleId, status);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("检查角色名称唯一性-唯一")
    void checkRoleNameUnique_True() {
        // Given
        when(roleMapper.checkRoleNameUnique("uniqueRole", 1L)).thenReturn(0);

        // When
        boolean result = roleService.checkRoleNameUnique("uniqueRole", 1L);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查角色名称唯一性-已存在")
    void checkRoleNameUnique_False() {
        // Given
        when(roleMapper.checkRoleNameUnique("existingRole", 1L)).thenReturn(1);

        // When
        boolean result = roleService.checkRoleNameUnique("existingRole", 1L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("检查角色编码唯一性-唯一")
    void checkRoleCodeUnique_True() {
        // Given
        when(roleMapper.checkRoleCodeUnique("ROLE_UNIQUE", 1L)).thenReturn(0);

        // When
        boolean result = roleService.checkRoleCodeUnique("ROLE_UNIQUE", 1L);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查角色编码唯一性-已存在")
    void checkRoleCodeUnique_False() {
        // Given
        when(roleMapper.checkRoleCodeUnique("ROLE_EXISTING", 1L)).thenReturn(1);

        // When
        boolean result = roleService.checkRoleCodeUnique("ROLE_EXISTING", 1L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("分配角色菜单-成功")
    void authRoleMenu_Success() {
        // Given
        Long roleId = 1L;
        List<Long> menuIds = List.of(1L, 2L, 3L);

        // When
        int result = roleService.authRoleMenu(roleId, menuIds);

        // Then
        assertThat(result).isEqualTo(1);
        verify(roleMapper).deleteRoleMenuByRoleId(roleId);
        verify(roleMapper).batchInsertRoleMenu(roleId, menuIds);
    }

    @Test
    @DisplayName("分配角色菜单-空菜单列表")
    void authRoleMenu_EmptyMenuIds() {
        // Given
        Long roleId = 1L;
        List<Long> menuIds = Collections.emptyList();

        // When
        int result = roleService.authRoleMenu(roleId, menuIds);

        // Then
        assertThat(result).isEqualTo(1);
        verify(roleMapper).deleteRoleMenuByRoleId(roleId);
        verify(roleMapper, never()).batchInsertRoleMenu(any(), any());
    }

    @Test
    @DisplayName("分配角色数据权限-自定义范围")
    void authRoleDataScope_Custom() {
        // Given
        Long roleId = 1L;
        Integer dataScope = DataScope.CUSTOM.getCode();
        List<Long> deptIds = List.of(1L, 2L);

        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);

        // When
        int result = roleService.authRoleDataScope(roleId, dataScope, deptIds);

        // Then
        assertThat(result).isEqualTo(1);
        verify(roleMapper).updateById(any(SysRole.class));
        verify(roleMapper).deleteRoleDeptByRoleId(roleId);
        verify(roleMapper).batchInsertRoleDept(roleId, deptIds);
    }

    @Test
    @DisplayName("分配角色数据权限-非自定义范围")
    void authRoleDataScope_NotCustom() {
        // Given
        Long roleId = 1L;
        Integer dataScope = DataScope.ALL.getCode();
        List<Long> deptIds = List.of(1L, 2L);

        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);

        // When
        int result = roleService.authRoleDataScope(roleId, dataScope, deptIds);

        // Then
        assertThat(result).isEqualTo(1);
        verify(roleMapper).updateById(any(SysRole.class));
        verify(roleMapper).deleteRoleDeptByRoleId(roleId);
        verify(roleMapper, never()).batchInsertRoleDept(any(), any());
    }

    private SysRole createRole(Long id, String name, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setStatus(0);
        return role;
    }

    private SysRoleVO createRoleVO(Long id, String name, String code) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(id);
        vo.setRoleName(name);
        vo.setRoleCode(code);
        vo.setStatus(0);
        return vo;
    }
}
