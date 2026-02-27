package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.domain.query.SysRoleQuery;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import java.util.List;

public interface SysRoleService extends IService<SysRole> {
    Page<SysRoleVO> selectRolePage(Page<SysRole> page, SysRoleQuery query);
    List<SysRoleVO> selectRoleAll();
    SysRoleVO selectRoleById(Long roleId);
    List<SysRoleVO> selectRolesByUserId(Long userId);
    Long insertRole(SysRoleDTO roleDTO);
    int updateRole(SysRoleDTO roleDTO);
    int deleteRoleByIds(List<Long> roleIds);
    int updateStatus(Long roleId, Integer status);
    boolean checkRoleNameUnique(String roleName, Long roleId);
    boolean checkRoleCodeUnique(String roleCode, Long roleId);
    int authRoleMenu(Long roleId, List<Long> menuIds);
    int authRoleDataScope(Long roleId, Integer dataScope, List<Long> deptIds);
}
