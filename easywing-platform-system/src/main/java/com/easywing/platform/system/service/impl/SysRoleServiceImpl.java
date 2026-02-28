package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.domain.query.SysRoleQuery;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import com.easywing.platform.system.enums.DataScope;
import com.easywing.platform.system.mapper.SysRoleMapper;
import com.easywing.platform.system.mapper.struct.RoleMapper;
import com.easywing.platform.system.service.SysRoleService;
import com.easywing.platform.system.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final RoleMapper roleMapperStruct;

    @Override
    public Page<SysRoleVO> selectRolePage(Page<SysRole> page, SysRoleQuery query) {
        LambdaQueryWrapper<SysRole> wrapper = buildQueryWrapper(query);
        Page<SysRole> rolePage = roleMapper.selectPage(page, wrapper);
        return PageUtil.convert(rolePage, roleMapperStruct::toVO);
    }

    @Override
    public List<SysRoleVO> selectRoleAll() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 0));
        List<SysRoleVO> roleVOs = roleMapperStruct.toVOList(roles);
        roleVOs.forEach(vo -> {
            DataScope dataScope = DataScope.values()[vo.getDataScope() - 1];
            if (dataScope != null) vo.setDataScopeDesc(dataScope.getDescription());
        });
        return roleVOs;
    }

    @Override
    public SysRoleVO selectRoleById(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            return null;
        }
        SysRoleVO vo = roleMapperStruct.toVO(role);
        DataScope dataScope = DataScope.values()[role.getDataScope() - 1];
        if (dataScope != null) vo.setDataScopeDesc(dataScope.getDescription());
        return vo;
    }

    @Override
    public List<SysRoleVO> selectRolesByUserId(Long userId) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        List<SysRoleVO> roleVOs = roleMapperStruct.toVOList(roles);
        roleVOs.forEach(vo -> {
            DataScope dataScope = DataScope.values()[vo.getDataScope() - 1];
            if (dataScope != null) vo.setDataScopeDesc(dataScope.getDescription());
        });
        return roleVOs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertRole(SysRoleDTO roleDTO) {
        if (!checkRoleNameUnique(roleDTO.getRoleName(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "角色名称已存在");
        }
        if (!checkRoleCodeUnique(roleDTO.getRoleCode(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "角色权限字符已存在");
        }
        SysRole role = roleMapperStruct.toEntity(roleDTO);
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRole(SysRoleDTO roleDTO) {
        if (roleDTO.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "角色ID不能为空");
        }
        if (!checkRoleNameUnique(roleDTO.getRoleName(), roleDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "角色名称已存在");
        }
        if (!checkRoleCodeUnique(roleDTO.getRoleCode(), roleDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "角色权限字符已存在");
        }
        SysRole role = roleMapperStruct.toEntity(roleDTO);
        return roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleByIds(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) return 0;
        return roleMapper.deleteBatchIds(roleIds);
    }

    @Override
    public int updateStatus(Long roleId, Integer status) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setStatus(status);
        return roleMapper.updateById(role);
    }

    @Override
    public boolean checkRoleNameUnique(String roleName, Long roleId) {
        return roleMapper.checkRoleNameUnique(roleName, roleId) == 0;
    }

    @Override
    public boolean checkRoleCodeUnique(String roleCode, Long roleId) {
        return roleMapper.checkRoleCodeUnique(roleCode, roleId) == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int authRoleMenu(Long roleId, List<Long> menuIds) {
        roleMapper.deleteRoleMenuByRoleId(roleId);
        if (!CollectionUtils.isEmpty(menuIds)) {
            roleMapper.batchInsertRoleMenu(roleId, menuIds);
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int authRoleDataScope(Long roleId, Integer dataScope, List<Long> deptIds) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setDataScope(dataScope);
        if (DataScope.CUSTOM.getCode() == dataScope && !CollectionUtils.isEmpty(deptIds)) {
            role.setDataScopeDeptIds(StrUtil.join(",", deptIds));
        }
        roleMapper.updateById(role);
        roleMapper.deleteRoleDeptByRoleId(roleId);
        if (DataScope.CUSTOM.getCode() == dataScope && !CollectionUtils.isEmpty(deptIds)) {
            roleMapper.batchInsertRoleDept(roleId, deptIds);
        }
        return 1;
    }

    private LambdaQueryWrapper<SysRole> buildQueryWrapper(SysRoleQuery query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (query == null) return wrapper;
        wrapper.like(StrUtil.isNotBlank(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .like(StrUtil.isNotBlank(query.getRoleCode()), SysRole::getRoleCode, query.getRoleCode())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByAsc(SysRole::getOrderNum);
        return wrapper;
    }
}
