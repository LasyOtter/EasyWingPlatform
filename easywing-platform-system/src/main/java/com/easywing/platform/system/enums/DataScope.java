package com.easywing.platform.system.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum DataScope {
    ALL(1, "全部数据权限"),
    CUSTOM(2, "自定义数据权限"),
    DEPT_ONLY(3, "本部门数据权限"),
    DEPT_AND_CHILD(4, "本部门及以下数据权限"),
    SELF_ONLY(5, "仅本人数据权限");

    private final int code;
    private final String description;

    /**
     * 根据code获取枚举
     *
     * @param code 数据权限编码
     * @return 数据权限枚举
     */
    public static DataScope of(int code) {
        return Arrays.stream(values())
                .filter(dataScope -> dataScope.getCode() == code)
                .findFirst()
                .orElse(SELF_ONLY);
    }
}
