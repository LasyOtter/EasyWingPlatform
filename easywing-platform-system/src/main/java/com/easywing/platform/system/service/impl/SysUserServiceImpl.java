package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.mapper.SysUserMapper;
import com.easywing.platform.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private static final String DEFAULT_PASSWORD = "123456";

    @Override
    public Page<SysUserVO> selectUserPage(Page<SysUser> page, SysUserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = buildQueryWrapper(query);
        Page<SysUser> userPage = userMapper.selectPage(page, wrapper);
        Page<SysUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public SysUserVO selectUserById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? convertToVO(user) : null;
    }

    @Override
    public SysUser selectUserByUsername(String username) {
        return userMapper.selectUserByUsername(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertUser(SysUserDTO userDTO) {
        validateUser(userDTO);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(userDTO, user);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setStatus(0);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(SysUserDTO userDTO) {
        if (userDTO.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户ID不能为空");
        }
        validateUser(userDTO);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(userDTO, user);
        return userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) return 0;
        return userMapper.deleteBatchIds(userIds);
    }

    @Override
    public int resetPassword(Long userId, String password) {
        return userMapper.resetPassword(userId, passwordEncoder.encode(password));
    }

    @Override
    public int updateStatus(Long userId, Integer status) {
        return userMapper.updateStatus(userId, status);
    }

    @Override
    public boolean checkUsernameUnique(String username) {
        return userMapper.checkUsernameUnique(username) == 0;
    }

    @Override
    public boolean checkPhoneUnique(String phone, Long userId) {
        return userMapper.checkPhoneUnique(phone, userId) == 0;
    }

    @Override
    public boolean checkEmailUnique(String email, Long userId) {
        return userMapper.checkEmailUnique(email, userId) == 0;
    }

    @Override
    public List<SysUserVO> exportUsers(SysUserQuery query) {
        List<SysUser> users = userMapper.selectList(buildQueryWrapper(query));
        return users.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public SysUserVO getCurrentUserInfo() {
        String userId = com.easywing.platform.system.util.SecurityUtils.getCurrentUserId();
        if (StrUtil.isEmpty(userId)) return null;
        return selectUserById(Long.parseLong(userId));
    }

    private LambdaQueryWrapper<SysUser> buildQueryWrapper(SysUserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (query == null) return wrapper;
        wrapper.like(StrUtil.isNotBlank(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StrUtil.isNotBlank(query.getNickname()), SysUser::getNickname, query.getNickname())
                .like(StrUtil.isNotBlank(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);
        return wrapper;
    }

    private SysUserVO convertToVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private void validateUser(SysUserDTO userDTO) {
        if (StrUtil.isNotBlank(userDTO.getPhone()) && !checkPhoneUnique(userDTO.getPhone(), userDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "手机号码已存在");
        }
        if (StrUtil.isNotBlank(userDTO.getEmail()) && !checkEmailUnique(userDTO.getEmail(), userDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "邮箱已存在");
        }
    }
}
