package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
    int checkRoleNameUnique(@Param("roleName") String roleName, @Param("roleId") Long roleId);
    int checkRoleCodeUnique(@Param("roleCode") String roleCode, @Param("roleId") Long roleId);
    int deleteRoleMenuByRoleId(@Param("roleId") Long roleId);
    int batchInsertRoleMenu(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
    int deleteRoleDeptByRoleId(@Param("roleId") Long roleId);
    int batchInsertRoleDept(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);
    List<com.easywing.platform.system.domain.entity.SysMenu> selectMenusByRoleId(@Param("roleId") Long roleId);
}
