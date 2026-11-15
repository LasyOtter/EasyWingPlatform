package com.easywing.platform.system.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SysDictTypeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String dictName;
    private String dictType;
    private Integer status;
    private String remark;
}
