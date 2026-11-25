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
package com.easywing.platform.aid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AID 漫剧工坊服务启动类。
 * <p>
 * 提供从剧本到成片的端到端创作能力：项目/分集/资产/分镜管理、四类 Provider 编排、
 * 生成任务调度与进度推送、视觉一致性、配音合成与成片导出。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.easywing.platform.aid")
@EnableAsync
public class AidApplication {

    public static void main(String[] args) {
        SpringApplication.run(AidApplication.class, args);
    }
}
