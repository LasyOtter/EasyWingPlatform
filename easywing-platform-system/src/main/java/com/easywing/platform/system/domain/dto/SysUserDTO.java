package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class SysUserDTO implements Serializable {
    private Long id;
    private Long deptId;
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 2, max = 20, message = "用户账号长度必须在2-20个字符之间")
    private String username;
    @NotBlank(message = "用户昵称不能为空")
    private String nickname;
    private String userType;
    @Email(message = "邮箱格式不正确")
    private String email;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String phone;
    private String gender;
    private Integer status;
    private List<Long> roleIds;
    private List<Long> postIds;
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
