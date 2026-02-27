package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import java.util.List;

public interface SysUserService extends IService<SysUser> {
    Page<SysUserVO> selectUserPage(Page<SysUser> page, SysUserQuery query);
    SysUserVO selectUserById(Long userId);
    SysUser selectUserByUsername(String username);
    Long insertUser(SysUserDTO userDTO);
    int updateUser(SysUserDTO userDTO);
    int deleteUserByIds(List<Long> userIds);
    int resetPassword(Long userId, String password);
    int updateStatus(Long userId, Integer status);
    boolean checkUsernameUnique(String username);
    boolean checkPhoneUnique(String phone, Long userId);
    boolean checkEmailUnique(String email, Long userId);
    List<SysUserVO> exportUsers(SysUserQuery query);
    SysUserVO getCurrentUserInfo();
}
