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
package com.easywing.platform.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DelayMessage {

    int level() default 1;

    String timeUnit() default "SECONDS";

    public static class DelayLevel {
        public static final int LEVEL_1S = 1;
        public static final int LEVEL_5S = 2;
        public static final int LEVEL_10S = 3;
        public static final int LEVEL_30S = 4;
        public static final int LEVEL_1M = 5;
        public static final int LEVEL_2M = 6;
        public static final int LEVEL_3M = 7;
        public static final int LEVEL_4M = 8;
        public static final int LEVEL_5M = 9;
        public static final int LEVEL_6M = 10;
        public static final int LEVEL_7M = 11;
        public static final int LEVEL_8M = 12;
        public static final int LEVEL_9M = 13;
        public static final int LEVEL_10M = 14;
        public static final int LEVEL_20M = 15;
        public static final int LEVEL_30M = 16;
        public static final int LEVEL_1H = 17;
        public static final int LEVEL_2H = 18;
    }
}
