package com.easywing.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private String roleName;
    private String roleCode;
    private String roleKey;
    private Integer dataScope;
    private String dataScopeDeptIds;
    private Integer menuCheckStrictly;
    private Integer deptCheckStrictly;
    private Integer orderNum;
    private Integer status;
    private String remark;
}
