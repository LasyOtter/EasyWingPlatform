package com.easywing.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.ASSIGN_ID)
    private Long operId;
    private Long tenantId;
    private String title;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private Integer operatorType;
    private String operName;
    private String operUrl;
    private String operIp;
    private String operLocation;
    private String operParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
    private LocalDateTime operTime;
    private Long costTime;
}
