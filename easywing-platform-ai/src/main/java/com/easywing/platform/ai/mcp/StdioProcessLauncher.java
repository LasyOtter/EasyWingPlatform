package com.easywing.platform.ai.mcp;

import java.io.IOException;

/**
 * 启动 MCP stdio 子进程的抽象，便于测试替换为假进程。
 */
public interface StdioProcessLauncher {

    Process start(McpServerConfig server) throws IOException;
}
