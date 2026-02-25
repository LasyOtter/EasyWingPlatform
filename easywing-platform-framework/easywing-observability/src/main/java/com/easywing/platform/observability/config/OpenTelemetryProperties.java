/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenTelemetry配置属性
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "easywing.otel")
public class OpenTelemetryProperties {

    /**
     * 是否启用OpenTelemetry
     */
    private boolean enabled = true;

    /**
     * 导出器配置
     */
    private ExporterConfig exporter = new ExporterConfig();

    /**
     * 追踪配置
     */
    private TracesConfig traces = new TracesConfig();

    /**
     * 指标配置
     */
    private MetricsConfig metrics = new MetricsConfig();

    /**
     * 日志配置
     */
    private LogsConfig logs = new LogsConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExporterConfig getExporter() {
        return exporter;
    }

    public void setExporter(ExporterConfig exporter) {
        this.exporter = exporter;
    }

    public TracesConfig getTraces() {
        return traces;
    }

    public void setTraces(TracesConfig traces) {
        this.traces = traces;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
    }

    public LogsConfig getLogs() {
        return logs;
    }

    public void setLogs(LogsConfig logs) {
        this.logs = logs;
    }

    /**
     * 导出器配置
     */
    public static class ExporterConfig {

        /**
         * OTLP导出器端点
         */
        private String endpoint = "http://localhost:4317";

        /**
         * OTLP配置
         */
        private OtlpConfig otlp = new OtlpConfig();

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public OtlpConfig getOtlp() {
            return otlp;
        }

        public void setOtlp(OtlpConfig otlp) {
            this.otlp = otlp;
        }
    }

    /**
     * OTLP配置
     */
    public static class OtlpConfig {

        /**
         * 是否启用OTLP导出器
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 追踪配置
     */
    public static class TracesConfig {

        /**
         * 采样器类型
         */
        private String sampler = "parentbased_always_on";

        /**
         * 采样率（用于traceidratio采样器）
         */
        private double samplingRate = 1.0;

        /**
         * 导出器类型
         */
        private String exporter = "otlp";

        public String getSampler() {
            return sampler;
        }

        public void setSampler(String sampler) {
            this.sampler = sampler;
        }

        public double getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
        }

        public String getExporter() {
            return exporter;
        }

        public void setExporter(String exporter) {
            this.exporter = exporter;
        }
    }

    /**
     * 指标配置
     */
    public static class MetricsConfig {

        /**
         * 是否启用指标
         */
        private boolean enabled = true;

        /**
         * 导出间隔（毫秒）
         */
        private long exportInterval = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getExportInterval() {
            return exportInterval;
        }

        public void setExportInterval(long exportInterval) {
            this.exportInterval = exportInterval;
        }
    }

    /**
     * 日志配置
     */
    public static class LogsConfig {

        /**
         * 是否启用日志
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
