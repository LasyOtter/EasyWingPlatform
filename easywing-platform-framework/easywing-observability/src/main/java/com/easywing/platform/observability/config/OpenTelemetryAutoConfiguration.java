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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OpenTelemetry自动配置
 * <p>
 * 配置OpenTelemetry SDK，包括：
 * <ul>
 *     <li>Tracer Provider - 链路追踪</li>
 *     <li>Meter Provider - 指标收集</li>
 *     <li>Propagator - 上下文传播</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(OpenTelemetryProperties.class)
@ConditionalOnProperty(prefix = "easywing.otel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryAutoConfiguration.class);

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${easywing.otel.exporter.endpoint:http://localhost:4317}")
    private String exporterEndpoint;

    @Value("${easywing.otel.traces.sampler:parentbased_always_on}")
    private String sampler;

    @Value("${easywing.otel.traces.exporter:otlp}")
    private String tracesExporter;

    @Bean
    @ConditionalOnMissingBean
    public Resource otelResource() {
        return Resource.getDefault().toBuilder()
                .put("service.name", applicationName)
                .put("service.instance.id", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local")
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Sampler otelSampler() {
        return switch (sampler.toLowerCase()) {
            case "always_on" -> Sampler.alwaysOn();
            case "always_off" -> Sampler.alwaysOff();
            case "traceidratio" -> Sampler.traceIdRatioBased(1.0);
            case "parentbased_always_on" -> Sampler.parentBased(Sampler.alwaysOn());
            case "parentbased_always_off" -> Sampler.parentBased(Sampler.alwaysOff());
            case "parentbased_traceidratio" -> Sampler.parentBased(Sampler.traceIdRatioBased(1.0));
            default -> Sampler.parentBased(Sampler.alwaysOn());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public TextMapPropagator otelTextMapPropagator() {
        return TextMapPropagator.composite(
                io.opentelemetry.context.propagation.TextMapPropagator.composite(
                        io.opentelemetry.api.trace.propagation.TraceContextPropagator.getInstance(),
                        io.opentelemetry.api.baggage.propagation.BaggagePropagator.getInstance(),
                        JaegerPropagator.getInstance()
                )
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider, TextMapPropagator propagator) {
        log.info("Initializing OpenTelemetry with application name: {}", applicationName);

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(io.opentelemetry.context.propagation.ContextPropagators.create(propagator))
                .buildAndRegisterGlobal();
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer otelTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName, "1.0.0");
    }

    @Bean
    @ConditionalOnProperty(prefix = "easywing.otel.exporter.otlp", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter otlpGrpcSpanExporter() {
        log.info("Configuring OTLP gRPC Span Exporter with endpoint: {}", exporterEndpoint);

        return io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter.builder()
                .setEndpoint(exporterEndpoint)
                .build();
    }
}
