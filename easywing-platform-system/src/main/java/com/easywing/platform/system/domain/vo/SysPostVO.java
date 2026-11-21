package com.easywing.platform.system.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

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
