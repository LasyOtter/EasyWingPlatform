package com.easywing.platform.system.datascope;

import com.easywing.platform.data.interceptor.DataScopeInterceptor;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.enums.DataScope;
import com.easywing.platform.system.mapper.SysDeptMapper;
import com.easywing.platform.system.mapper.SysRoleMapper;
import com.easywing.platform.system.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统数据权限处理器
 * <p>
 * 实现数据权限处理器接口，提供当前用户的数据权限信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDataScopeHandler implements DataScopeInterceptor.DataScopeHandler {

    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public DataScopeInterceptor.DataScopeInfo getDataScopeInfo() {
        // 获取当前登录用户
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }

        Long deptId = SecurityUtils.getCurrentDeptId();
        boolean isAdmin = SecurityUtils.isAdmin();

        // 超级管理员直接返回
        if (isAdmin) {
            return new SystemDataScopeInfo(userId, deptId, DataScopeInterceptor.DataScopeType.ALL, true, null, null);
        }

        // 获取用户的角色数据权限范围
        DataScope dataScope = getDataScope(userId);
        DataScopeInterceptor.DataScopeType scopeType = convertToInterceptorType(dataScope);

        // 获取子部门ID列表
        Set<Long> childDeptIds = null;
        if (scopeType == DataScopeInterceptor.DataScopeType.DEPT_AND_CHILD && deptId != null) {
            childDeptIds = deptMapper.selectChildDeptIds(deptId);
            if (childDeptIds == null) {
                childDeptIds = new HashSet<>();
            }
        }

        // 获取自定义部门ID列表
        List<Long> customDeptIds = null;
        if (scopeType == DataScopeInterceptor.DataScopeType.CUSTOM) {
            customDeptIds = roleMapper.selectCustomDataScopeDeptIds(userId);
        }

        return new SystemDataScopeInfo(userId, deptId, scopeType, false, childDeptIds, customDeptIds);
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID
     */
    private Long getCurrentUserId() {
        String userIdStr = SecurityUtils.getCurrentUserId();
        if (userIdStr == null) {
            return null;
        }
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取用户的数据权限范围
     * <p>
     * 取用户所有角色中数据权限范围最大的（数字越小范围越大）
     *
     * @param userId 用户ID
     * @return 数据权限范围
     */
    private DataScope getDataScope(Long userId) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);

        if (CollectionUtils.isEmpty(roles)) {
            return DataScope.SELF_ONLY; // 默认只能看自己的数据
        }

        // 取最小值（ALL=1, CUSTOM=2, ... SELF_ONLY=5）
        int minScopeCode = roles.stream()
                .map(SysRole::getDataScope)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(DataScope.SELF_ONLY.getCode());

        return DataScope.of(minScopeCode);
    }

    /**
     * 将系统DataScope转换为拦截器DataScopeType
     *
     * @param dataScope 系统数据权限
     * @return 拦截器数据权限类型
     */
    private DataScopeInterceptor.DataScopeType convertToInterceptorType(DataScope dataScope) {
        return switch (dataScope) {
            case ALL -> DataScopeInterceptor.DataScopeType.ALL;
            case CUSTOM -> DataScopeInterceptor.DataScopeType.CUSTOM;
            case DEPT_ONLY -> DataScopeInterceptor.DataScopeType.DEPT_ONLY;
            case DEPT_AND_CHILD -> DataScopeInterceptor.DataScopeType.DEPT_AND_CHILD;
            case SELF_ONLY -> DataScopeInterceptor.DataScopeType.SELF_ONLY;
        };
    }

    /**
     * 系统数据权限信息实现类
     */
    public record SystemDataScopeInfo(
            Long userId,
            Long deptId,
            DataScopeInterceptor.DataScopeType dataScope,
            boolean isAdmin,
            Set<Long> childDeptIds,
            List<Long> customDeptIds
    ) implements DataScopeInterceptor.DataScopeInfo {
    }
}
