package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SysDictDataDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer dictSort;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签长度不能超过100个字符")
    private String dictLabel;

    @NotBlank(message = "字典键值不能为空")
    @Size(max = 100, message = "字典键值长度不能超过100个字符")
    private String dictValue;

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;

    @Size(max = 100, message = "样式属性长度不能超过100个字符")
    private String cssClass;

    @Size(max = 100, message = "表格样式长度不能超过100个字符")
    private String listClass;

    private Integer isDefault;
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
