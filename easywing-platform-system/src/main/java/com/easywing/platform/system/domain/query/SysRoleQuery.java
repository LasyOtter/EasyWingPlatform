package com.easywing.platform.system.domain.query;

import java.io.Serializable;
import lombok.Data;

@Data
public class SysRoleQuery implements Serializable {
    private String roleName;
    private String roleCode;
    private Integer status;
    private String beginTime;
    private String endTime;
}
