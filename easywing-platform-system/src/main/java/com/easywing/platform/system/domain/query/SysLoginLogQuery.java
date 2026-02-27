package com.easywing.platform.system.domain.query;

import lombok.Data;
import java.io.Serializable;

@Data
public class SysLoginLogQuery implements Serializable {
    private String username;
    private String clientId;
    private Integer status;
    private String beginTime;
    private String endTime;
}
