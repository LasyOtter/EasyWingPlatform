package com.easywing.platform.system.domain.query;

import java.io.Serializable;
import lombok.Data;

@Data
public class SysLoginLogQuery implements Serializable {
    private String username;
    private String clientId;
    private Integer status;
    private String beginTime;
    private String endTime;
}
