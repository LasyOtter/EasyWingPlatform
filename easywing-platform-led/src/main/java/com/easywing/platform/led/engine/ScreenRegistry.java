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
package com.easywing.platform.led.engine;

import com.easywing.platform.led.config.ConfigLoader;
import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.RefreshConfig;
import com.easywing.platform.led.config.Screen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 屏注册表：集中加载并索引所有屏定义与其刷新配置。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class ScreenRegistry {

    private final LedConfig config;
    private final ConfigLoader loader;
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, RefreshConfig> refreshes = new LinkedHashMap<>();

    public ScreenRegistry(Path baseDir, LedConfig config, ConfigLoader loader) {
        this.config = config;
        this.loader = loader;
        List<Screen> loaded = loader.loadScreens(config);
        for (Screen s : loaded) {
            screens.put(s.getId(), s);
        }
        for (Screen s : loaded) {
            RefreshConfig rc = loader.loadRefresh(s.getId(), config);
            if (rc != null) {
                refreshes.put(s.getId(), rc);
            }
        }
    }

    public LedConfig config() {
        return config;
    }

    public List<Screen> all() {
        return Collections.unmodifiableList(new ArrayList<>(screens.values()));
    }

    public Screen get(String id) {
        return screens.get(id);
    }

    public boolean has(String id) {
        return screens.containsKey(id);
    }

    public RefreshConfig refresh(String screenId) {
        return refreshes.get(screenId);
    }

    public Map<String, RefreshConfig> allRefreshes() {
        return Collections.unmodifiableMap(refreshes);
    }

    @Override
    public String toString() {
        return "ScreenRegistry{屏数=" + screens.size() + ", 刷新配置数=" + refreshes.size() + "}";
    }
}
