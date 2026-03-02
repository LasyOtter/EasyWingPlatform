package com.easywing.platform.system.datascope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.easywing.platform.data.interceptor.DataScopeInterceptor;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.enums.DataScope;
import com.easywing.platform.system.mapper.SysDeptMapper;
import com.easywing.platform.system.mapper.SysRoleMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * SystemDataScopeHandler 单元测试
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SystemDataScopeHandlerTest {

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysDeptMapper deptMapper;

    @InjectMocks
    private SystemDataScopeHandler dataScopeHandler;

    @BeforeEach
    void setUp() {
        // 设置SecurityContext
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("获取数据权限信息-未登录用户")
    void getDataScopeInfo_NotAuthenticated() {
        // Given - 没有设置认证信息

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取数据权限信息-超级管理员")
    void getDataScopeInfo_AdminUser() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("1");
        when(jwt.getClaim("dept_id")).thenReturn(100L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getDeptId()).isEqualTo(100L);
        assertThat(result.isAdmin()).isTrue();
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.ALL);
    }

    @Test
    @DisplayName("获取数据权限信息-仅本人权限")
    void getDataScopeInfo_SelfOnly() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaim("dept_id")).thenReturn(200L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        SysRole role = new SysRole();
        role.setDataScope(DataScope.SELF_ONLY.getCode());

        when(roleMapper.selectRolesByUserId(2L)).thenReturn(List.of(role));

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(2L);
        assertThat(result.isAdmin()).isFalse();
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.SELF_ONLY);
    }

    @Test
    @DisplayName("获取数据权限信息-本部门权限")
    void getDataScopeInfo_DeptOnly() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("3");
        when(jwt.getClaim("dept_id")).thenReturn(300L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        SysRole role = new SysRole();
        role.setDataScope(DataScope.DEPT_ONLY.getCode());

        when(roleMapper.selectRolesByUserId(3L)).thenReturn(List.of(role));

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(3L);
        assertThat(result.getDeptId()).isEqualTo(300L);
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.DEPT_ONLY);
    }

    @Test
    @DisplayName("获取数据权限信息-本部门及以下权限")
    void getDataScopeInfo_DeptAndChild() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("4");
        when(jwt.getClaim("dept_id")).thenReturn(400L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        SysRole role = new SysRole();
        role.setDataScope(DataScope.DEPT_AND_CHILD.getCode());

        when(roleMapper.selectRolesByUserId(4L)).thenReturn(List.of(role));
        when(deptMapper.selectChildDeptIds(400L)).thenReturn(Set.of(401L, 402L));

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(4L);
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.DEPT_AND_CHILD);
        assertThat(result.getChildDeptIds()).containsExactlyInAnyOrder(401L, 402L);
    }

    @Test
    @DisplayName("获取数据权限信息-自定义权限")
    void getDataScopeInfo_Custom() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("5");
        when(jwt.getClaim("dept_id")).thenReturn(500L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        SysRole role = new SysRole();
        role.setDataScope(DataScope.CUSTOM.getCode());

        when(roleMapper.selectRolesByUserId(5L)).thenReturn(List.of(role));
        when(roleMapper.selectCustomDataScopeDeptIds(5L)).thenReturn(List.of(501L, 502L));

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(5L);
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.CUSTOM);
        assertThat(result.getCustomDeptIds()).containsExactlyInAnyOrder(501L, 502L);
    }

    @Test
    @DisplayName("获取数据权限信息-无角色默认仅本人")
    void getDataScopeInfo_NoRoles_DefaultSelfOnly() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("6");
        when(jwt.getClaim("dept_id")).thenReturn(600L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        when(roleMapper.selectRolesByUserId(6L)).thenReturn(Collections.emptyList());

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(6L);
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.SELF_ONLY);
    }

    @Test
    @DisplayName("获取数据权限信息-多角色取最小权限")
    void getDataScopeInfo_MultipleRoles_TakeMinScope() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("7");
        when(jwt.getClaim("dept_id")).thenReturn(700L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.getAuthorities()).thenReturn(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // 多个角色：DEPT_ONLY(3) 和 SELF_ONLY(5)，应该取最小值DEPT_ONLY(3)
        SysRole role1 = new SysRole();
        role1.setDataScope(DataScope.DEPT_ONLY.getCode());
        SysRole role2 = new SysRole();
        role2.setDataScope(DataScope.SELF_ONLY.getCode());

        when(roleMapper.selectRolesByUserId(7L)).thenReturn(List.of(role1, role2));

        // When
        DataScopeInterceptor.DataScopeInfo result = dataScopeHandler.getDataScopeInfo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(7L);
        // 应该取最小值，即DEPT_ONLY(3)
        assertThat(result.getDataScope()).isEqualTo(DataScopeInterceptor.DataScopeType.DEPT_ONLY);
    }
}
