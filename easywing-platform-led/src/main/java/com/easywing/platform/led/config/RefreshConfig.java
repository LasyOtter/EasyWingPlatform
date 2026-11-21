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
package com.easywing.platform.led.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 刷新配置（refresh/*.yml）。
 *
 * <p>注意：内码刷新协议 %disp 后的编号是「内码区在节目中的添加顺序(0起)」，
 * 不是 areaNo，因此绑定用 areaIndex。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class RefreshConfig {

    /** 刷新周期，如 30s / 1m */
    private String interval = "30s";

    private List<Binding> bindings = new ArrayList<>();

    public long intervalMillis() {
        return DurationParser.parseMillis(interval, 30_000L);
    }

    /**
     * 刷新绑定。
     */
    @Data
    public static class Binding {
        /** 目标屏 id（可省略，默认与文件名对应） */
        private String screen;
        /**
         * 目标节目号（可省略，默认 0）。
         *
         * <p>{@code areaIndex} 是「内码区在节目内的添加顺序」，不同节目各自从 0 编号，
         * 因此多节目屏应显式指定 {@code programNo}。
         */
        private Integer programNo;
        /** 内码区顺序号 0 起 */
        private int areaIndex;
        /** 颜色符号对应的颜色名 */
        private String color = "green";
        /** %disp 特效 0-9 */
        private int effect;
        /** 文本模板，支持 {占位符} */
        private String template = "";
        /** 数据源：命名数据源 id，或内建类型名 */
        private String source = "counter";

        // counter 源（内建）
        private Double counterStart;
        private Double counterStep;
        private Integer counterDecimals;

        // now 源（内建）
        private String nowPattern = "yyyy-MM-dd HH:mm:ss";

        // http 源（内建简写，命名数据源请写到 led.yml 的 sources 里）
        private String httpUrl;
        private String httpField;

        // script 源（内建）
        private String script;
    }
}
