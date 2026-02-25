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
package com.easywing.platform.gateway;

import com.easywing.platform.gateway.hints.GatewayRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * EasyWing API网关启动类
 * <p>
 * 高性能企业级API网关，具备以下核心功能：
 * <ul>
 *     <li>JWT校验（支持RS256/ES256，本地缓存优化）</li>
 *     <li>分布式限流（Redis + Lua脚本，多级策略）</li>
 *     <li>灰度发布（流量染色，权重路由）</li>
 *     <li>日志脱敏（正则匹配，零分配优化）</li>
 * </ul>
 * <p>
 * 性能指标：
 * <ul>
 *     <li>启动时间：< 3秒（JVM）/ < 500ms（Native）</li>
 *     <li>QPS：> 10000（单节点，4C8G）</li>
 *     <li>内存占用：< 200MB（JVM）/ < 80MB（Native）</li>
 *     <li>延迟P99：< 10ms</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
@ImportRuntimeHints(GatewayRuntimeHints.class)
public class EasyWingGatewayApplication {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        SpringApplication.run(EasyWingGatewayApplication.class, args);
        long startupTime = System.currentTimeMillis() - startTime;
        System.out.println("EasyWing Gateway started in " + startupTime + "ms");
    }
}
