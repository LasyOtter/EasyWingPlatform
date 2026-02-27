package com.easywing.platform.system.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysDeptVO implements Serializable {
    private Long id;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptCode;
    private Integer orderNum;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private List<SysDeptVO> children;
}
