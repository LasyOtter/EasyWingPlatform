package com.easywing.platform.ai.agentdef;

/**
 * Agent 定义的可见性范围。
 * <p>
 * {@code PRIVATE} 仅所有者可见（默认）；{@code USER} 共享给指定用户；
 * {@code GROUP} 共享给指定群组；{@code PUBLIC} 租户内所有人可见。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public enum AgentVisibility {
    PRIVATE,
    USER,
    GROUP,
    PUBLIC;

    /**
     * 宽松解析：空值或非法值回退为 {@code PRIVATE}。
     */
    public static AgentVisibility from(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException e) {
            return PRIVATE;
        }
    }

    /**
     * 严格解析：非法值抛出 {@link IllegalArgumentException}，用于入参校验。
     */
    public static AgentVisibility parse(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
