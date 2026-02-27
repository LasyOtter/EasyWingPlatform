package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SysLoginLogVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long infoId;
    private Long tenantId;
    private String username;
    private String clientId;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
