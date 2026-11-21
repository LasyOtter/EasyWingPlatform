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
import java.util.Locale;
import lombok.Data;

/**
 * 屏定义（screens/*.yml 的 screen 节点）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class Screen {

    private String id;
    private String name;
    /** 局域网填 IP，广域网填控制卡背面 MAC/ID */
    private String ip;

    private int width;
    private int height;
    /** 1 单色, 2 双基色, 3 三基色(C全彩), 4 X系列 */
    private int colorType;
    /** 1-5；C 系列(C2M/C4M/C2S/C4A)传 0 */
    private int grayLevel;
    /** 0 flash, 3 ram；留空时取系统默认 */
    private Integer saveType;

    /** 卡型号，覆盖根配置 sdk.ledType：0 T/A/U/XC/W, 1 E, 2 X, 3 C, 4 E5/E6/C8, 5 D2/D4/D6 */
    private int ledType;
    /** RGB 顺序，仅 C2M/C4M 有效 */
    private int rgb;
    /** 连接方式：tcp / udp / serial / wan */
    private String connection = "tcp";
    /**
     * SDK 实现：vision（1.0.8）/ listen（五代 V2014）/ onbon（仰邦 bx06）。
     * 留空时取根配置 sdk.provider。
     */
    private String sdkProvider;

    /** 屏参微调（可选）：校正时间、屏名、串口波特率等。 */
    private ScreenTuning tuning;
    /** 扫描/模组参数（可选，E 系列等）。 */
    private ScanTuning scan;
    /** 定时亮度组（可选，最多 3 组）：{startHour,startMinute,endHour,endMinute,brightness}。 */
    private List<int[]> brightnessTimer;
    /** 定时开关机组（可选，最多 3 组）：{startHour,startMinute,endHour,endMinute}。 */
    private List<int[]> powerTimer;
    /** 搜索控制器时是否广播。 */
    private boolean searchBroadcast = true;
    /** 搜索控制器结果 ini 路径；为空时用系统临时文件。 */
    private String searchIniPath;

    private List<Program> programs = new ArrayList<>();

    /** 实际生效的 SDK 实现名。 */
    public String effectiveProvider(LedConfig config) {
        if (sdkProvider != null && !sdkProvider.isBlank()) {
            return sdkProvider.trim().toLowerCase(Locale.ROOT);
        }
        return config.getSdk().getProvider() == null
                        || config.getSdk().getProvider().isBlank()
                ? "vision"
                : config.getSdk().getProvider().trim().toLowerCase(Locale.ROOT);
    }

    public int effectiveSaveType(LedConfig config) {
        return saveType != null ? saveType : config.getSystem().getDefaultSaveType();
    }

    /** 区域是否超出屏范围 */
    public boolean contains(Rect r) {
        return r.getLeft() >= 0 && r.getTop() >= 0 && r.right() <= width && r.bottom() <= height;
    }

    /**
     * 屏参微调。
     */
    @Data
    public static class ScreenTuning {
        /** 屏名（SetScreenInfo.screenname）。 */
        private String screenName;
        /** 串口波特率（SetScreenInfo.baud）。 */
        private Integer baud;
        /** 下发前校正控制器时间。 */
        private boolean adjustTime;
        /** 开机/关屏：true 开屏，false 关屏。 */
        private Boolean powerOn;
        /** 亮度 1-16。 */
        private Integer brightness;
    }

    /**
     * 扫描/模组参数（E 系列等）。
     */
    @Data
    public static class ScanTuning {
        /** 模组宽度，-1 表示不设置。 */
        private Integer moduleWidth = -1;
        /** 列点数。 */
        private Integer columnDot = 0;
        /** 扫描方式：16 扫 0，P16 扫 6，P10 扫 8。 */
        private Integer scanStyle;
        /** 数据高度，-1 表示不设置。 */
        private Integer dataHeight = -1;
        /** 模组基色 1/2/3。 */
        private Integer dbColor;
        /** 译码方式：0 译码，1 直译。 */
        private Integer decode;
        /** OE：1 有效，0 无效。 */
        private Integer oe;
        /** DA：1 高有效，0 低有效。 */
        private Integer da;
        /** 红绿交换：0 R+G，1 G+R。 */
        private Integer rgSwap;
    }
}
