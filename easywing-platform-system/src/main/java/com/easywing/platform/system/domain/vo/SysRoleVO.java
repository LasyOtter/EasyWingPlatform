package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysRoleVO implements Serializable {
    private Long id;
    private String roleName;
    private String roleCode;
    private String roleKey;
    private Integer dataScope;
    private String dataScopeDesc;
    private String dataScopeDeptIds;
    private Integer menuCheckStrictly;
    private Integer deptCheckStrictly;
    private Integer orderNum;
    private Integer status;
    private LocalDateTime createTime;
    private List<Long> menuIds;
    private List<Long> deptIds;
    private String remark;
}
