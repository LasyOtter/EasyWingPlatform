package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SysPostVO implements Serializable {
    private Long id;
    private String postCode;
    private String postName;
    private String postCategory;
    private Integer orderNum;
    private Integer status;
    private LocalDateTime createTime;
    private String remark;
}
