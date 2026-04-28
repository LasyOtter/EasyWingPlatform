package com.easywing.platform.ai.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse {
    private String jsonrpc;
    private String id;
    private Object result;
    private McpError error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpError {
        private int code;
        private String message;
    }

    public static McpResponse success(Object result, String id) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();
    }

    public static McpResponse error(int code, String message, String id) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(McpError.builder().code(code).message(message).build())
                .build();
    }
}