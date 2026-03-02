package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 查询受保护的用户ID（超级管理员、系统内置用户）
     */
    @Select("SELECT user_id FROM sys_user WHERE is_protected = 1 OR user_type = '00'")
    List<Long> selectProtectedUserIds();

    /**
     * 批量查询用户信息（带部门ID）
     */
    @Select("<script>" +
            "SELECT user_id, dept_id, create_by FROM sys_user WHERE user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<SysUser> selectBatchIdsWithDept(@Param("userIds") List<Long> userIds);
}
