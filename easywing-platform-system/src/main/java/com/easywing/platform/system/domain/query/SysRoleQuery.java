package com.easywing.platform.system.domain.query;

import lombok.Data;
import java.io.Serializable;

@Data
public class SysRoleQuery implements Serializable {
    private String roleName;
    private String roleCode;
    private Integer status;
    private String beginTime;
    private String endTime;
}
