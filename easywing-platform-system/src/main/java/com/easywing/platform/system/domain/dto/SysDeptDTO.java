package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

@Data
public class SysDeptDTO implements Serializable {
    private Long id;
    private Long parentId;
    private String deptName;
    @NotBlank(message = "部门编码不能为空")
    private String deptCode;
    private Integer orderNum;
    private String leader;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String phone;
    @Email(message = "邮箱格式不正确")
    private String email;
    private Integer status;
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
