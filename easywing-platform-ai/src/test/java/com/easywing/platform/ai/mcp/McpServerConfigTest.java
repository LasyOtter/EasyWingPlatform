package com.easywing.platform.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.config.AiProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    @Test
    void defaultsToHttpTransport() {
        assertThat(McpServerConfig.builder().build().effectiveType()).isEqualTo(McpServerConfig.TYPE_HTTP);
        assertThat(McpServerConfig.builder().type(" STDIO ").build().isStdio()).isTrue();
    }

    @Test
    void validateAcceptsHttpServer() {
        McpServerConfig.builder()
                .type(McpServerConfig.TYPE_HTTP)
                .url("http://mcp.local")
                .build()
                .validate("internal");
    }

    @Test
    void validateRejectsHttpServerWithoutUrl() {
        assertThatThrownBy(() -> McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_HTTP)
                        .build()
                        .validate("internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void validateRejectsStdioServerWithoutCommand() {
        assertThatThrownBy(() -> McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_STDIO)
                        .build()
                        .validate("internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void validateRejectsInvalidServerName() {
        assertThatThrownBy(() -> McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_STDIO)
                        .command("node")
                        .build()
                        .validate("bad name!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
    }

    @Test
    void validateRejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_HTTP)
                        .url("http://mcp.local")
                        .timeoutSeconds(0)
                        .build()
                        .validate("internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeoutSeconds");
    }

    @Test
    void resolveMergesLegacyServerTokenAndReturnsCopy() {
        AiProperties.McpConfig mcp = new AiProperties.McpConfig();
        mcp.setServers(new HashMap<>(Map.of(
                "legacy",
                McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_HTTP)
                        .url("http://mcp.local")
                        .build())));
        mcp.setServerTokens(new HashMap<>(Map.of("legacy", "token-123")));

        McpServerConfig resolved = mcp.resolve("legacy");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("legacy");
        assertThat(resolved.getBearerToken()).isEqualTo("token-123");
        assertThat(mcp.resolve("missing")).isNull();

        resolved.setUrl("http://changed");
        assertThat(mcp.getServers().get("legacy").getUrl()).isEqualTo("http://mcp.local");
    }

    @Test
    void resolveKeepsExplicitBearerTokenOverLegacyToken() {
        AiProperties.McpConfig mcp = new AiProperties.McpConfig();
        mcp.setServers(new HashMap<>(Map.of(
                "explicit",
                McpServerConfig.builder()
                        .type(McpServerConfig.TYPE_HTTP)
                        .url("http://mcp.local")
                        .bearerToken("primary")
                        .build())));
        mcp.setServerTokens(new HashMap<>(Map.of("explicit", "legacy")));

        assertThat(mcp.resolve("explicit").getBearerToken()).isEqualTo("primary");
    }
}
