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
import com.listenvision.led;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 灵信 vision SDK 网关：负责 DLL 加载、初始化、错误码检查，并把 SDK 的 int 错误码转成异常。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class SdkGateway {

    private static volatile boolean initialized = false;
    private static LedConfig config;

    private SdkGateway() {}

    /** 加载 DLL 并初始化卡型号（进程内只调用一次）。 */
    public static synchronized void init(LedConfig cfg) {
        if (initialized) {
            return;
        }
        config = cfg;
        Path path = Paths.get(cfg.getSdk().getDllPath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new LedException("加载DLL", -1, "DLL 不存在: " + path);
        }
        System.load(path.toString());
        led.InitLedType(cfg.getSdk().getLedType());
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * DLL 期望的字符串编码。
     */
    public static Charset encoding() {
        if (config == null
                || config.getSdk().getEncoding() == null
                || config.getSdk().getEncoding().isBlank()) {
            return Charset.defaultCharset();
        }
        try {
            return Charset.forName(config.getSdk().getEncoding().trim());
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    /** 把 Java 字符串按 DLL 期望编码转成字节。 */
    public static byte[] toDllBytes(String text) {
        return text == null ? new byte[0] : text.getBytes(encoding());
    }

    /** 执行 SDK 调用并在错误码非 0 时抛异常。 */
    public static void call(String action, SdkCall call) {
        int code = call.run();
        if (code != 0) {
            throw new LedException(action, code, describe(code));
        }
    }

    /** 执行 SDK 调用，返回错误码而不抛异常（用于批量场景收集结果）。 */
    public static int tryRun(String action, SdkCall call) {
        try {
            return call.run();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 执行 SDK 调用并返回错误码；非 0 时抛异常。 */
    public static int callReturn(String action, SdkCall call) {
        int code = call.run();
        if (code != 0) {
            throw new LedException(action, code, describe(code));
        }
        return code;
    }

    /** 错误码 → 描述：优先取 SDK 的 GetErrorCodeInfo，失败时回退到本地码表。 */
    private static String describe(int code) {
        String detail = ErrorCode.describe(code);
        try {
            String official = led.GetErrorCodeInfo(code);
            if (official != null && !official.isBlank()) {
                detail = official;
            }
        } catch (Throwable ignored) {
            // GetErrorCodeInfo 可能因 DLL 未就绪而失败，忽略
        }
        return detail;
    }

    @FunctionalInterface
    public interface SdkCall {
        int run();
    }
}
