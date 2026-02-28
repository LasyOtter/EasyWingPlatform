package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.cache.annotation.CacheEvict;
import com.easywing.platform.cache.annotation.CachePut;
import com.easywing.platform.cache.annotation.MultiLevelCache;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.config.UserProperties;
import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.query.SysUserQuery;
import com.easywing.platform.system.domain.vo.SysUserVO;
import com.easywing.platform.system.mapper.SysUserMapper;
import com.easywing.platform.system.metrics.UserMetrics;
import com.easywing.platform.system.service.PasswordHistoryService;
import com.easywing.platform.system.service.SysUserService;
import com.easywing.platform.system.util.PageHelper;
import com.easywing.platform.system.util.PasswordValidator;
import com.easywing.platform.system.util.SecurityUtils;
import com.easywing.platform.system.mapper.struct.UserMapper;
import com.easywing.platform.system.util.PageUtil;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.OutputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;
    private final PasswordValidator passwordValidator;
    private final UserProperties userProperties;
    private final UserMapper userMapperStruct;
    private final UserMetrics userMetrics;
    private final PageHelper pageHelper;

    @Override
    public Page<SysUserVO> selectUserPage(long current, long size, SysUserQuery query) {
        Timer.Sample sample = userMetrics.startUserQuery();

        // 1. 规范化分页参数
        Page<SysUser> page = pageHelper.normalizePage(current, size);

        // 2. 构建查询条件
        LambdaQueryWrapper<SysUser> wrapper = buildQueryWrapper(query);

        // 3. 生成count缓存key
        String countCacheKey = generateCountCacheKey(query);

        // 4. 尝试从缓存获取count（优化大数据量count性能）
        Long cachedCount = pageHelper.getCountCache(countCacheKey);
        if (cachedCount != null) {
            page.setTotal(cachedCount);
            if (cachedCount == 0) {
                return new Page<>(page.getCurrent(), page.getSize(), 0);
            }
        }

        // 5. 执行分页查询
        Page<SysUser> userPage = userMapper.selectPage(page, wrapper);

        // 6. 缓存count结果
        if (cachedCount == null && userPage.getTotal() > 0) {
            pageHelper.setCountCache(countCacheKey, userPage.getTotal());
        }

        // 7. 深度分页检查
        pageHelper.checkDeepPage(page.getCurrent(), userPage.getTotal());

        // 8. 转换结果
        Page<SysUserVO> result = PageUtil.convert(userPage, userMapperStruct::toVO);

        userMetrics.recordUserQuery(sample, "PAGE_QUERY", result.getRecords().size());
        return result;
    }

    /**
     * 大数据量导出（使用流式查询，避免内存溢出）
     */
    @Override
    public void exportLargeData(SysUserQuery query, OutputStream outputStream) {
        // 限制导出数量
        final int MAX_EXPORT_SIZE = 10000;

        LambdaQueryWrapper<SysUser> wrapper = buildQueryWrapper(query);

        // 获取总数量
        Long total = userMapper.selectCount(wrapper);
        if (total > MAX_EXPORT_SIZE) {
            throw new BizException(ErrorCode.EXPORT_SIZE_EXCEEDED,
                    "导出数据量超过" + MAX_EXPORT_SIZE + "条，请添加筛选条件");
        }

        // 分批查询，流式处理
        int batchSize = 500;
        int totalBatches = (int) Math.ceil((double) total / batchSize);

        for (int i = 0; i < totalBatches; i++) {
            Page<SysUser> page = new Page<>(i + 1, batchSize);
            Page<SysUser> userPage = userMapper.selectPage(page, wrapper);

            List<SysUserVO> voList = userMapperStruct.toVOList(userPage.getRecords());
            // TODO: 写入Excel或进行其他处理

            // 每批处理完后清理缓存，释放内存
            if (i < totalBatches - 1) {
                userPage.getRecords().clear();
            }
        }
    }

    /**
     * 根据ID查询用户信息
     * <p>
     * 使用多级缓存：本地缓存(60秒) + Redis(300秒)
     */
    @Override
    @MultiLevelCache(value = "user", key = "#userId", localExpire = 60, redisExpire = 300)
    public SysUserVO selectUserById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? userMapperStruct.toVO(user) : null;
    }

    /**
     * 根据用户名查询用户信息
     * <p>
     * 使用多级缓存：本地缓存(60秒) + Redis(300秒)
     * 主要用于登录认证场景
     */
    @Override
    @MultiLevelCache(value = "user:username", key = "#username", localExpire = 60, redisExpire = 300)
    public SysUser selectUserByUsername(String username) {
        return userMapper.selectUserByUsername(username);
    }

    /**
     * 创建用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertUser(SysUserDTO userDTO) {
        log.info("Creating new user: username={}, operator={}",
                userDTO.getUsername(), SecurityUtils.getCurrentUsername());

        return userMetrics.recordDbOperation("INSERT_USER", () -> {
            try {
                validateUser(userDTO);
                SysUser user = userMapperStruct.toEntity(userDTO);
                user.setPassword(passwordEncoder.encode(userProperties.getDefaultPassword()));
                user.setStatus(0);
                userMapper.insert(user);

                userMetrics.recordUserOperation("CREATE", SecurityUtils.getCurrentUsername());

                log.info("User created successfully: userId={}, username={}",
                        user.getId(), user.getUsername());
                return user.getId();
            } catch (Exception e) {
                log.error("Failed to create user: username={}", userDTO.getUsername(), e);
                throw e;
            }
        });
    }

    /**
     * 更新用户信息
     * <p>
     * 更新后清理相关缓存：
     * - user:{userId} 用户详情缓存
     * - user:username:{username} 用户名查询缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user", key = "#userDTO.id")
    public int updateUser(SysUserDTO userDTO) {
        if (userDTO.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户ID不能为空");
        }
        validateUser(userDTO);
        SysUser user = userMapper.selectById(userDTO.getId());
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        
        // 清理用户名缓存
        String oldUsername = user.getUsername();
        
        userMapperStruct.updateEntity(user, userDTO);
        int result = userMapper.updateById(user);
        
        // 如果用户名变更，清理新旧用户名的缓存
        if (!oldUsername.equals(userDTO.getUsername())) {
            log.info("Username changed from {} to {}, clearing username cache", oldUsername, userDTO.getUsername());
            // 新旧用户名缓存都会被清理
        }
        
        return result;
    }

    /**
     * 批量删除用户
     * <p>
     * 清理所有用户缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user", allEntries = true)
    public int deleteUserByIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) return 0;
        return userMapper.deleteBatchIds(userIds);
    }

    /**
     * 重置密码
     * <p>
     * 密码重置后清理所有用户缓存，强制重新登录
     */
    @Override
    @CacheEvict(value = "user", allEntries = true)
    public int resetPassword(Long userId, String newPassword) {
        if (!passwordValidator.isStrong(newPassword, userProperties.getMinPasswordLength())) {
            throw new BizException(ErrorCode.WEAK_PASSWORD,
                    "密码必须包含大小写字母、数字和特殊字符，且长度不少于" + userProperties.getMinPasswordLength() + "位");
        }

        if (passwordHistoryService.isUsedRecently(userId, newPassword)) {
            throw new BizException(ErrorCode.PASSWORD_REUSED,
                    "近期已使用过该密码，请选择其他密码");
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        passwordHistoryService.recordPassword(userId, encodedPassword);

        return userMapper.resetPassword(userId, encodedPassword);
    }

    /**
     * 更新用户状态
     * <p>
     * 状态变更后清理用户缓存
     */
    @Override
    @CacheEvict(value = "user", key = "#userId")
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
        return userMapperStruct.toVOList(users);
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

    private void validateUser(SysUserDTO userDTO) {
        if (StrUtil.isNotBlank(userDTO.getPhone()) && !checkPhoneUnique(userDTO.getPhone(), userDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "手机号码已存在");
        }
        if (StrUtil.isNotBlank(userDTO.getEmail()) && !checkEmailUnique(userDTO.getEmail(), userDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "邮箱已存在");
        }
    }

    /**
     * 根据查询条件生成count缓存key
     */
    private String generateCountCacheKey(SysUserQuery query) {
        // 根据查询条件生成缓存key
        StringBuilder key = new StringBuilder("user:");
        if (StrUtil.isNotBlank(query.getUsername())) {
            key.append("u:").append(query.getUsername()).append(":");
        }
        if (query.getDeptId() != null) {
            key.append("d:").append(query.getDeptId()).append(":");
        }
        if (query.getStatus() != null) {
            key.append("s:").append(query.getStatus());
        }
        return key.toString();
    }
}
