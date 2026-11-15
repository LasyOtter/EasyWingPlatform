package com.easywing.platform.ai.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息数据
     */
    private Object data;

    /**
     * 时间戳
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        // 客户端发送
        EXECUTE,          // 执行 Agent
        CANCEL,           // 取消执行
        PING,             // 心跳
        CONFIGURE,        // 配置更新

        // 服务器发送
        CONNECTED,        // 连接成功
        AGENT_EVENT,      // Agent 事件
        ERROR,            // 错误
        CANCELLED,        // 已取消
        PONG,             // 心跳响应
        CONFIGURED        // 配置已更新
    }
}
