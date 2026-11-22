package com.easywing.platform.ai.mcp;

import reactor.core.publisher.Mono;

/**
 * MCP 传输层抽象：HTTP/SSE 与本地 stdio 两种实现共用同一套 JSON-RPC 语义。
 */
public interface McpTransport {

    /** 传输方式标识，对应 {@link McpServerConfig} 的 type。 */
    String type();

    /** 建立连接并完成 initialize 能力协商；返回是否成功。 */
    Mono<Boolean> initialize(McpServerConfig server);

    /**
     * 发送一条 JSON-RPC 消息。
     *
     * @param notification 为 true 时表示通知消息（如 notifications/initialized），不等待响应
     */
    Mono<McpResponse> send(McpServerConfig server, McpRequest request, boolean notification);

    /** 释放该 Server 占用的传输资源。 */
    void close(McpServerConfig server);
}
