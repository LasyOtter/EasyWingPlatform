package com.easywing.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private Long deptId;
    private String username;
    private String nickname;
    private String userType;
    private String email;
    private String phone;
    private String gender;
    private String avatar;
    private String password;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime pwdUpdateTime;
    private LocalDateTime pwdExpireTime;
    private Integer mfaEnabled;
    private String mfaSecret;
    private Integer failedAttempts;
    private LocalDateTime lockedUntil;
    private String remark;
}
