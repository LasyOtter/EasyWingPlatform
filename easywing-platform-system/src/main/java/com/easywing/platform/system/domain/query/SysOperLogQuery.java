package com.easywing.platform.system.domain.query;

import lombok.Data;
import java.io.Serializable;

@Data
public class SysOperLogQuery implements Serializable {
    private String title;
    private Integer businessType;
    private String operName;
    private Integer status;
    private String beginTime;
    private String endTime;
}
