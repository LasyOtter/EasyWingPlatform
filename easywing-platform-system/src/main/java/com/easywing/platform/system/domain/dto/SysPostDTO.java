package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

@Data
public class SysPostDTO implements Serializable {
    private Long id;
    @NotBlank(message = "岗位编码不能为空")
    private String postCode;
    @NotBlank(message = "岗位名称不能为空")
    private String postName;
    private String postCategory;
    private Integer orderNum;
    private Integer status;
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
