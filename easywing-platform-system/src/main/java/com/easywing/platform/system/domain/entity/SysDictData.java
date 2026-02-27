package com.easywing.platform.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.easywing.platform.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {
    private Integer dictSort;
    private String dictLabel;
    private String dictValue;
    private String dictType;
    private String cssClass;
    private String listClass;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
