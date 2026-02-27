package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    SysUser selectUserByUsername(@Param("username") String username);
    SysUser selectUserByIdWithDept(@Param("userId") Long userId);
    List<SysUser> selectUsersByDeptId(@Param("deptId") Long deptId);
    List<SysUser> selectUsersByRoleId(@Param("roleId") Long roleId);
    int updateLoginInfo(@Param("userId") Long userId, @Param("loginIp") String loginIp);
    int resetPassword(@Param("userId") Long userId, @Param("password") String password);
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);
    int checkUsernameUnique(@Param("username") String username);
    int checkPhoneUnique(@Param("phone") String phone, @Param("userId") Long userId);
    int checkEmailUnique(@Param("email") String email, @Param("userId") Long userId);
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
    int deleteUserRoleByUserId(@Param("userId") Long userId);
}
