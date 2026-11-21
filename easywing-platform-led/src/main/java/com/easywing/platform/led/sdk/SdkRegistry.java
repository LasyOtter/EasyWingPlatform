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
package com.easywing.platform.led.sdk;

import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.sdk.listen.ListenSdk;
import com.easywing.platform.led.sdk.onbon.OnbonSdk;
import com.easywing.platform.led.sdk.vision.VisionSdk;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SDK 实现注册表：按 {@code sdk.provider} / {@code screen.sdkProvider} 选择 {@link LedSdk}。
 *
 * <p>进程内每个实现只初始化一次（{@link LedSdk#init} 幂等由实现保证）。
 * 缺依赖的实现只跳过并告警，不拖垮整个进程。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SdkRegistry {

    private static final Map<String, LedSdk> INSTANCES = new ConcurrentHashMap<>();

    static {
        registerSafe("vision", VisionSdk::new);
        registerSafe("listen", ListenSdk::new);
        registerSafe("onbon", OnbonSdk::new);
    }

    private static void registerSafe(String name, java.util.function.Supplier<LedSdk> factory) {
        try {
            register(factory.get());
        } catch (Throwable t) {
            log.warn("[SDK] 实现 {} 不可用（缺少依赖？）已跳过: {}", name, t.toString());
        }
    }

    private SdkRegistry() {}

    public static void register(LedSdk sdk) {
        INSTANCES.put(sdk.provider().toLowerCase(Locale.ROOT), sdk);
    }

    /** 按 provider 名取实现；未注册时抛 {@link LedException}。 */
    public static LedSdk of(String provider) {
        String key = provider == null || provider.isBlank()
                ? "vision"
                : provider.trim().toLowerCase(Locale.ROOT);
        LedSdk sdk = INSTANCES.get(key);
        if (sdk == null) {
            throw new LedException("SdkRegistry", -1, "未注册的 SDK 实现: " + provider + "（可用: " + INSTANCES.keySet() + "）");
        }
        return sdk;
    }

    /** 屏实际使用的实现。 */
    public static LedSdk forScreen(Screen screen, LedConfig config) {
        return of(screen.effectiveProvider(config));
    }

    /** 已注册的 SDK 实现名列表（按注册顺序）。 */
    public static java.util.List<String> available() {
        return new java.util.ArrayList<>(INSTANCES.keySet());
    }

    /** 已注册的 SDK 实现实例（用于启动时初始化）。 */
    public static java.util.Collection<LedSdk> instances() {
        return INSTANCES.values();
    }

    /** 是否已注册。 */
    public static boolean contains(String provider) {
        return INSTANCES.containsKey(provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT));
    }
}
