package com.easywing.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.ASSIGN_ID)
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
