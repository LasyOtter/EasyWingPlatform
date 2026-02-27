package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysMenuVO implements Serializable {
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
    private LocalDateTime createTime;
    private List<SysMenuVO> children;
    private String remark;
}
