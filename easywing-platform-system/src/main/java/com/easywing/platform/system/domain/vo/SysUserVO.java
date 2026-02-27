package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysUserVO implements Serializable {
    private Long id;
    private Long deptId;
    private String deptName;
    private String username;
    private String nickname;
    private String userType;
    private String email;
    private String phone;
    private String gender;
    private String avatar;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime createTime;
    private List<SysRoleVO> roles;
    private List<SysPostVO> posts;
    private List<Long> roleIds;
    private List<Long> postIds;
    private String remark;
}
