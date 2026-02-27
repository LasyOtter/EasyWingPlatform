package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
public class SysDictDataVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Integer dictSort;
    private String dictLabel;
    private String dictValue;
    private String dictType;
    private String cssClass;
    private String listClass;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
