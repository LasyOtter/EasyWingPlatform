package com.easywing.platform.system.domain.vo;

import com.easywing.platform.system.enums.DataScope;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户信息
 * <p>
 * 封装当前登录用户的基本信息和权限信息
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 数据权限范围
     */
    private DataScope dataScope;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 是否超级管理员
     */
    private boolean admin;

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取部门ID
     *
     * @return 部门ID
     */
    public Long getDeptId() {
        return deptId;
    }

    /**
     * 获取数据权限范围
     *
     * @return 数据权限范围
     */
    public DataScope getDataScope() {
        return dataScope;
    }

    /**
     * 是否超级管理员
     *
     * @return true表示是超级管理员
     */
    public boolean isAdmin() {
        return admin;
    }
}
