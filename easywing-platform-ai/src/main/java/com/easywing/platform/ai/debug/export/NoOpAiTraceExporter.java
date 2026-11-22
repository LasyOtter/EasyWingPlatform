package com.easywing.platform.ai.debug.export;

import com.easywing.platform.ai.debug.AgentTrace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认链路导出器：不向任何外部系统发送数据。
 * <p>
 * 当 {@code easywing.ai.trace.exporter} 缺省或取值为 {@code none} 时生效，
 * 保证容器中始终存在唯一一个 {@link AiTraceExporter} 实现。
 */
@Component
@ConditionalOnProperty(prefix = "easywing.ai.trace", name = "exporter", havingValue = "none", matchIfMissing = true)
public class NoOpAiTraceExporter implements AiTraceExporter {

    @Override
    public void export(AgentTrace trace) {
        // 默认不导出
    }
}
