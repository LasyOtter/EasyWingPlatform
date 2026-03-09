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
package com.easywing.platform.messaging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "easywing.messaging")
public class MessagingProperties {

    private String adapterType = "kafka";

    private boolean enabled = true;

    private RetryProperties retry = new RetryProperties();

    private TimeoutProperties timeout = new TimeoutProperties();

    private DeadLetterProperties deadLetter = new DeadLetterProperties();

    @Data
    public static class RetryProperties {
        private int maxAttempts = 3;
        private long initialInterval = 1000L;
        private double multiplier = 2.0;
        private long maxInterval = 10000L;
    }

    @Data
    public static class TimeoutProperties {
        private long send = 30000L;
        private long receive = 30000L;
    }

    @Data
    public static class DeadLetterProperties {
        private boolean enabled = false;
        private String queueName = "DLQ";
    }
}
