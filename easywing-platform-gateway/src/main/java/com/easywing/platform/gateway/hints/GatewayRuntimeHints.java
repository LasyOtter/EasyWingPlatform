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
package com.easywing.platform.gateway.hints;

import com.easywing.platform.gateway.filter.jwt.JwtClaims;
import com.easywing.platform.gateway.filter.jwt.JwtValidationFilter;
import com.easywing.platform.gateway.filter.ratelimit.RateLimitFilter;
import com.easywing.platform.gateway.filter.gray.GrayReleaseFilter;
import com.easywing.platform.gateway.filter.logging.LoggingFilter;
import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.JwtProperties;
import com.easywing.platform.gateway.properties.RateLimitProperties;
import com.easywing.platform.gateway.properties.GrayProperties;
import com.easywing.platform.gateway.properties.LoggingProperties;
import com.easywing.platform.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * GraalVM Native Image运行时提示配置
 * <p>
 * 为反射、资源和代理提供必要的配置，确保Native Image正常运行
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class GatewayRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerReflectionHints(hints);
        registerResourceHints(hints);
        registerProxyHints(hints);
        registerSerializationHints(hints);
    }

    private void registerReflectionHints(RuntimeHints hints) {
        hints.reflection()
            .registerType(GatewayProperties.class, MemberCategory.values())
            .registerType(JwtProperties.class, MemberCategory.values())
            .registerType(RateLimitProperties.class, MemberCategory.values())
            .registerType(GrayProperties.class, MemberCategory.values())
            .registerType(LoggingProperties.class, MemberCategory.values())
            .registerType(JwtClaims.class, MemberCategory.values())
            .registerType(JwtValidationFilter.class, MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(RateLimitFilter.class, MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(GrayReleaseFilter.class, MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(LoggingFilter.class, MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(RedisRateLimiter.class, MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerResourceHints(RuntimeHints hints) {
        hints.resources()
            .registerResource(new ClassPathResource("application.yml"))
            .registerResource(new ClassPathResource("application.yaml"))
            .registerResource(new ClassPathResource("log4j2-spring.xml"))
            .registerResource(new ClassPathResource("META-INF/spring.factories"))
            .registerResource(new ClassPathResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
            .registerPattern("*.yml")
            .registerPattern("*.yaml")
            .registerPattern("*.json")
            .registerPattern("*.xml");
    }

    private void registerProxyHints(RuntimeHints hints) {
        hints.proxies()
            .registerJdkProxy(KeyResolver.class)
            .registerJdkProxy(org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory.class)
            .registerJdkProxy(org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.class);
    }

    private void registerSerializationHints(RuntimeHints hints) {
        hints.serialization()
            .registerType(JwtClaims.class);
    }
}
