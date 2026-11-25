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
package com.easywing.platform.aid.controller;

import com.easywing.platform.aid.task.TaskQueue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务自检端点。
 * <p>
 * 该路径在 {@code easywing.security.public-paths} 中放行，供探活使用；
 * 返回队列类型与积压量，便于在不暴露 actuator 的前提下判断后端是否可接单。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/aid")
@Tag(name = "AID 健康检查")
@RequiredArgsConstructor
public class AidHealthController {

    private final TaskQueue taskQueue;

    @GetMapping("/health")
    @Operation(summary = "服务自检：返回队列类型与积压量")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("queueType", taskQueue.type());
        body.put("pending", taskQueue.pendingCount());
        body.put("inFlight", taskQueue.inFlightCount());
        return ResponseEntity.ok(body);
    }
}
