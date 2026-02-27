package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SysOnlineVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String userId;
    private String username;
    private String nickname;
    private String deptName;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private LocalDateTime loginTime;
    private LocalDateTime expireTime;
}
