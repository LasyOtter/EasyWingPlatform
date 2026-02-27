package com.easywing.platform.system.domain.query;

import lombok.Data;
import java.io.Serializable;

@Data
public class SysUserQuery implements Serializable {
    private Long deptId;
    private String username;
    private String nickname;
    private String userType;
    private String phone;
    private Integer status;
    private String beginTime;
    private String endTime;
}
