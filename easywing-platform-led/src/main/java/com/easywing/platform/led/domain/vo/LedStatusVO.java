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
package com.easywing.platform.led.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 服务状态视图：SDK 实现、已加载屏与刷新任务一览。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class LedStatusVO implements Serializable {

    private boolean enabled;
    private boolean fileConfigEnabled;
    private boolean autoDeploy;
    private boolean autoRefresh;
    private boolean refreshStarted;
    private List<String> sdkProviders = new ArrayList<>();
    private List<String> screens = new ArrayList<>();
    private List<String> refreshes = new ArrayList<>();
    private String namedDataSources;
}
