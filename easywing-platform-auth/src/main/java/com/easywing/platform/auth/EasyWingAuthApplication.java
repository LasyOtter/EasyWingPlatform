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
package com.easywing.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * EasyWing 认证服务启动类
 * <p>
 * 提供统一认证授权能力：
 * <ul>
 *     <li>JWT令牌签发（RS256）</li>
 *     <li>令牌刷新</li>
 *     <li>令牌注销（Redis黑名单）</li>
 *     <li>JWK Set公钥端点（供网关/资源服务器验证）</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class EasyWingAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyWingAuthApplication.class, args);
    }
}
