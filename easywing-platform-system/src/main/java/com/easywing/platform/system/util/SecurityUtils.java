package com.easywing.platform.system.util;

import com.easywing.platform.system.domain.vo.LoginUser;
import com.easywing.platform.system.enums.DataScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static Jwt getJwt() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    public static String getCurrentUserId() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    public static String getCurrentUsername() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            Authentication authentication = getAuthentication();
            return authentication != null ? authentication.getName() : "system";
        }
        return jwt.getClaimAsString("preferred_username");
    }

    public static Long getCurrentDeptId() {
        Jwt jwt = getJwt();
        if (jwt == null) return null;
        Object deptId = jwt.getClaim("dept_id");
        if (deptId == null) deptId = jwt.getClaim("deptId");
        if (deptId instanceof Number number) return number.longValue();
        if (deptId instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public static Long getCurrentTenantId() {
        Jwt jwt = getJwt();
        if (jwt == null) return 0L;
        Object tenantId = jwt.getClaim("tenant_id");
        if (tenantId == null) tenantId = jwt.getClaim("tenantId");
        if (tenantId instanceof Number number) return number.longValue();
        if (tenantId instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException e) { return 0L; }
        }
        return 0L;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    public static boolean isAdmin() {
        Authentication authentication = getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    public static <T> T getClaim(String claimName, Class<T> clazz) {
        Jwt jwt = getJwt();
        if (jwt == null) return null;
        return jwt.getClaim(claimName);
    }

    /**
     * 获取当前登录用户详细信息
     *
     * @return 登录用户信息
     */
    public static LoginUser getLoginUser() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return null;
        }

        LoginUser loginUser = new LoginUser();
        String userId = jwt.getSubject();
        if (userId != null) {
            try {
                loginUser.setUserId(Long.parseLong(userId));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        loginUser.setUsername(jwt.getClaimAsString("preferred_username"));
        loginUser.setDeptId(getCurrentDeptId());
        loginUser.setTenantId(getCurrentTenantId());
        loginUser.setAdmin(isAdmin());

        // 从JWT中提取角色信息
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            loginUser.setRoles(roles);
        }

        // 从JWT中提取数据权限范围
        Integer dataScopeCode = jwt.getClaim("data_scope");
        if (dataScopeCode != null) {
            loginUser.setDataScope(DataScope.of(dataScopeCode));
        }

        return loginUser;
    }

    /**
     * 获取当前用户的数据权限范围
     *
     * @return 数据权限范围，如果未登录返回仅本人权限
     */
    public static DataScope getDataScope() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return DataScope.SELF_ONLY;
        }
        DataScope dataScope = loginUser.getDataScope();
        return dataScope != null ? dataScope : DataScope.SELF_ONLY;
    }
}
