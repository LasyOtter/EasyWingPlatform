package com.easywing.platform.system.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

@Data
public class SysMenuDTO implements Serializable {
    private Long id;
    private String menuName;
    private Long parentId;
    private Integer orderNum;
    private String path;
    private String component;
    private String queryParam;
    private Integer isFrame;
    private Integer isCache;
    private String menuType;
    private Integer visible;
    private Integer status;
    private String perms;
    private String permsType;
    private String icon;
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
