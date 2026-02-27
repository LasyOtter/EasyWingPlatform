package com.easywing.platform.system.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OperatorType {
    OTHER(0, "其他"),
    MANAGE(1, "后台用户"),
    MOBILE(2, "手机端用户");

    private final int code;
    private final String description;
}
