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
package com.easywing.platform.gateway.filter.gray;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.gateway.properties.GatewayProperties;
import com.easywing.platform.gateway.properties.GrayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灰度发布全局过滤器
 * <p>
 * 核心功能：
 * <ul>
 *     <li>流量染色（基于Header/Cookie/Parameter）</li>
 *     <li>版本路由（Nacos元数据标签匹配）</li>
 *     <li>金丝雀发布策略：百分比路由、用户维度、权重路由</li>
 *     <li>A/B测试支持</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *     <li>路由规则本地缓存（监听Nacos变化）</li>
 *     <li>一致性哈希（相同用户路由到相同版本）</li>
 *     <li>零拷贝路由决策</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class GrayReleaseFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseFilter.class);
    private static final String GRAY_VERSION_ATTR = "grayVersion";
    private static final String CONSISTENT_HASH_ATTR = "consistentHash";

    private final GrayProperties properties;
    private final Cache<String, String> userVersionCache;
    private final Map<String, WeightedSelector> serviceSelectors = new ConcurrentHashMap<>();

    public GrayReleaseFilter(GatewayProperties gatewayProperties) {
        this.properties = gatewayProperties.getGray();
        this.userVersionCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
        
        initSelectors();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String grayVersion = resolveGrayVersion(exchange);
        
        if (grayVersion == null) {
            String userId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_USER_ID);
            Route route = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            
            if (route != null && userId != null) {
                grayVersion = resolveVersionByStrategy(route.getId(), userId, exchange);
            }
        }
        
        if (grayVersion != null) {
            exchange.getAttributes().put(GRAY_VERSION_ATTR, grayVersion);
            
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(properties.getHeaderName(), grayVersion)
                    .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }
        
        return chain.filter(exchange);
    }

    private String resolveGrayVersion(ServerWebExchange exchange) {
        String version = resolveFromHeader(exchange);
        if (version != null) {
            return version;
        }
        
        version = resolveFromCookie(exchange);
        if (version != null) {
            return version;
        }
        
        version = resolveFromParameter(exchange);
        return version;
    }

    private String resolveFromHeader(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(properties.getHeaderName());
        return StringUtils.hasText(header) ? header : null;
    }

    private String resolveFromCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(properties.getCookieName());
        return cookie != null && StringUtils.hasText(cookie.getValue()) ? cookie.getValue() : null;
    }

    private String resolveFromParameter(ServerWebExchange exchange) {
        String param = exchange.getRequest().getQueryParams().getFirst(properties.getParameterName());
        return StringUtils.hasText(param) ? param : null;
    }

    private String resolveVersionByStrategy(String serviceId, String userId, ServerWebExchange exchange) {
        GrayProperties.ServiceConfig serviceConfig = findServiceConfig(serviceId);
        if (serviceConfig == null) {
            return properties.getDefaultVersion();
        }
        
        String cachedVersion = userVersionCache.getIfPresent(userId);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        String version = null;
        
        for (GrayProperties.RuleConfig rule : serviceConfig.getRules()) {
            if (matchesRule(rule, userId, exchange)) {
                version = rule.getTargetVersion();
                break;
            }
        }
        
        if (version == null) {
            version = selectByWeight(serviceId, userId, serviceConfig);
        }
        
        if (version != null) {
            userVersionCache.put(userId, version);
        }
        
        return version != null ? version : serviceConfig.getDefaultVersion();
    }

    private boolean matchesRule(GrayProperties.RuleConfig rule, String userId, ServerWebExchange exchange) {
        return switch (rule.getType()) {
            case "user_id" -> userId != null && userId.equals(rule.getMatchValue());
            case "user_ids" -> {
                String[] ids = rule.getMatchValue().split(",");
                for (String id : ids) {
                    if (userId != null && userId.equals(id.trim())) {
                        yield true;
                    }
                }
                yield false;
            }
            case "header" -> {
                String headerValue = exchange.getRequest().getHeaders().getFirst(rule.getMatchKey());
                yield headerValue != null && headerValue.equals(rule.getMatchValue());
            }
            case "percentage" -> {
                int percentage = Integer.parseInt(rule.getMatchValue());
                int hash = consistentHash(userId);
                yield (hash % 100) < percentage;
            }
            default -> false;
        };
    }

    private String selectByWeight(String serviceId, String userId, GrayProperties.ServiceConfig config) {
        if (config.getVersions().isEmpty()) {
            return config.getDefaultVersion();
        }
        
        WeightedSelector selector = serviceSelectors.computeIfAbsent(serviceId, 
                k -> new WeightedSelector(config.getVersions()));
        
        int hash = consistentHash(userId);
        return selector.select(hash);
    }

    private int consistentHash(String key) {
        if (key == null) {
            return ThreadLocalRandom.current().nextInt();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24) | 
                   ((digest[1] & 0xFF) << 16) | 
                   ((digest[2] & 0xFF) << 8) | 
                   (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode();
        }
    }

    private GrayProperties.ServiceConfig findServiceConfig(String serviceId) {
        for (GrayProperties.ServiceConfig config : properties.getServices()) {
            if (config.getServiceId().equals(serviceId)) {
                return config;
            }
        }
        return null;
    }

    private void initSelectors() {
        for (GrayProperties.ServiceConfig service : properties.getServices()) {
            if (!service.getVersions().isEmpty()) {
                serviceSelectors.put(service.getServiceId(), new WeightedSelector(service.getVersions()));
            }
        }
    }

    public void refreshSelectors() {
        serviceSelectors.clear();
        initSelectors();
        log.info("Gray release selectors refreshed");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    private static class WeightedSelector {
        private final NavigableMap<Integer, String> weightMap = new TreeMap<>();
        private int totalWeight = 0;

        WeightedSelector(List<GrayProperties.VersionConfig> versions) {
            for (GrayProperties.VersionConfig version : versions) {
                totalWeight += version.getWeight();
                weightMap.put(totalWeight, version.getVersion());
            }
        }

        String select(int hash) {
            int value = Math.abs(hash % totalWeight);
            return weightMap.ceilingEntry(value + 1) != null 
                    ? weightMap.ceilingEntry(value + 1).getValue() 
                    : weightMap.lastEntry().getValue();
        }
    }
}