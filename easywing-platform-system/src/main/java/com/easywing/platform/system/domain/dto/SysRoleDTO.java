package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class SysRoleDTO implements Serializable {
    private Long id;
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过50个字符")
    private String roleName;
    @NotBlank(message = "角色权限字符不能为空")
    @Size(max = 50, message = "角色权限字符长度不能超过50个字符")
    private String roleCode;
    private String roleKey;
    private Integer dataScope;
    private String dataScopeDeptIds;
    private Integer menuCheckStrictly;
    private Integer deptCheckStrictly;
    private Integer orderNum;
    private Integer status;
    private List<Long> menuIds;
    private List<Long> deptIds;
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
