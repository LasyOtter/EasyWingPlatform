package com.easywing.platform.ai.workspace;

/**
 * 工作区类型：个人工作区或托管工作区。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public enum WorkspaceType {
    PERSONAL,
    MANAGED;

    /** 宽松解析：非法值回退为 {@link #PERSONAL}（用于入参缺省）。 */
    public static WorkspaceType from(String value) {
        if (value == null || value.isBlank()) {
            return PERSONAL;
        }
        try {
            return WorkspaceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PERSONAL;
        }
    }

    /** 严格解析：非法值直接抛异常（用于显式校验）。 */
    public static WorkspaceType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("工作区类型不能为空");
        }
        try {
            return WorkspaceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("工作区类型仅支持 PERSONAL / MANAGED");
        }
    }
}
