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

import java.util.List;
import lombok.Data;

/**
 * 区域。type 决定用哪个 SDK 方法。
 *
 * <p>支持的类型（{@link #type}）：
 * <pre>
 *   neima     内码区         -> AddNeiMaArea        (可实时刷新 %disp)
 *   multiline 多行文本区     -> AddMultiLineTextToImageTextArea[Back]
 *   singleline 单行文本区    -> AddSinglelineTextToImageTextArea[Back]
 *   static    静态文本区     -> AddStaticTextToImageTextArea[Back]
 *   voice     语音区         -> AddVoiceArea        (可实时刷新)
 *   clock     数字时钟       -> AddDigitalClockArea
 *   time      倒计时/计时区  -> AddTimeArea（计时方向用 countMode：1 正计/2 倒计，默认 2）
 *   image     图片/文件区    -> AddImageTextArea + AddFileToImageTextArea
 *   qrcode    二维码区       -> AddQRCodeArea       (仅新 C2M/C4M)
 *   border    边框           -> AddBorder
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
public class Area {

    /** 区域号 1-255 */
    private int areaNo;
    /** 区域类型，见类注释 */
    private String type;
    /** 屏坐标（部分区域如 voice 不需要） */
    private Rect rect;

    // ---- 通用文本属性 ----
    private String content;
    private Font font;

    // ---- 文本区特效 ----
    /**
     * 进入特效：文本/图片区 0-38；
     * 内码区 0-11（0立即显示 1左移 2右移 3上移 4下移 5连续左移
     * 6连续右移 7连续上移 8连续下移 9闪烁 10冒泡 11下雪）。
     */
    private int inStyle;
    /** 速度 1-255，越大越慢 */
    private int speed;
    /** 停留时间 1-65535 秒 */
    private int delay;

    // ---- 内码区专用 ----
    /** 字号 16 / 24 / 32 */
    private Integer fontSize;
    /** 颜色名或 BBGGRR 十六进制 */
    private String fontColor;
    /** 内码区在节目内的顺序号(0起)，用于 %disp 刷新绑定；默认按出现顺序自动分配 */
    private Integer neimaIndex;

    // ---- 多行/静态文本专用 ----
    /** 0 左对齐, 1 右对齐, 2 水平居中 */
    private Integer alignment;
    /** 0 置顶, 1 垂直居中 */
    private Boolean vcenter;
    /** 背景色（Back 变体） */
    private Integer backColor;
    /** 旋转 0/1/2/3 = 0/90/180/270 度（Back 变体） */
    private Integer rotation;

    // ---- 文本来源 ----
    /** 0 字符串, 1 文件路径（仅 txt/rtf）；默认 0 */
    private Integer addType;

    // ---- 语音区 ----
    /** 播放次数 */
    private Integer playCount;

    // ---- 数字时钟 ----
    /** 需要显示的字段：year, week, month, day, hour, minute, second */
    private List<String> show;
    /** 日期格式 0-7 */
    private Integer dateFormat;
    /** 星期格式 0 星期X, 1 Monday, 2 Mon. */
    private Integer weekFormat;
    /** 时间格式 0-6 */
    private Integer timeFormat;
    /** 多行显示 0/1 */
    private Integer multiLine;
    /** 各部分颜色，默认继承 font.color */
    private String dateColor;

    private String weekColor;
    private String timeColor;

    // ---- 倒计时/计时区 ----
    /**
     * 显示格式 0-4（沿用 vision {@code AddTimeArea.ShowStyle} 语义）：
     * <pre>
     *   0  xx days xx minutes xx seconds
     *   1  xx天xx分xx秒
     *   2  xxDayxxHourxxMinxxSec
     *   3  XXdXXhXXmXXs
     *   4  xx:xx:xx:xx
     * </pre>
     * 注意：<b>不是</b> listen {@code AddTimerArea.mode}（1 正计/2 倒计）的语义，
     * 方向由 {@link #countMode} 表达。
     */
    private Integer showStyle;
    /**
     * 计时方式：1 正计时（从 0 累加），2 倒计时（到 {@link #endTime} 为止）。默认 2。
     */
    private Integer countMode;
    /** 自定义文字 */
    private String txtStr;
    /** 结束时间 "yyyy-MM-dd HH:mm:ss" */
    private String endTime;
    /** 是否显示 天/时/分/秒 */
    private Boolean showDay;

    private Boolean showHour;
    private Boolean showMinute;
    private Boolean showSecond;

    // ---- 图片区 ----
    private String filePath;
    /** 1 前景区(默认), 0 背景区 */
    private Integer layout;

    // ---- 二维码区 ----
    private Integer lowVersion;
    private Integer highVersion;
    /** 纠错级别 0自适应 1L 2M 3Q 4H */
    private Integer errLevel;
    /** 0xff 自适应, 0-7 手动 */
    private Integer maskVersion;

    private Integer blankPointNum;
    /** 底色 0红 1绿 2黄 3蓝 4紫 5青 6白 */
    private Integer baseCodeColor;

    private Integer colorReverse;
    /** 0 黑屏, 1 显示默认码 */
    private Integer defaultShow;

    private String defaultCode;

    // ---- 边框 ----
    /** 0 单色, 1 炫色, 2 全彩 */
    private Integer borderType;
    /** 炫色/全彩时的边框值，参考 PC 客户端选项 */
    private Integer borderValue;
    /** 单色时生效，BBGGRR */
    private Integer borderColor;
    /** 0 固定, 1 顺时针, 2 逆时针, 3 闪烁 */
    private Integer borderStyle;
    /** 0-31，越大越慢 */
    private Integer borderSpeed;

    public Font effectiveFont() {
        return font != null ? font : new Font();
    }

    /** 是否使用了带背景色/旋转的 Back 变体接口。 */
    public boolean useBackVariant() {
        return (backColor != null || rotation != null)
                && !("clock".equalsIgnoreCase(type)
                        || "time".equalsIgnoreCase(type)
                        || "neima".equalsIgnoreCase(type)
                        || "voice".equalsIgnoreCase(type)
                        || "image".equalsIgnoreCase(type)
                        || "qrcode".equalsIgnoreCase(type)
                        || "border".equalsIgnoreCase(type));
    }

    /**
     * 字体属性。
     */
    @Data
    public static class Font {
        private String name = "宋体";
        private int size = 16;
        /** BBGGRR 或颜色名 */
        private String color = "red";

        private boolean bold;
        private boolean italic;
        private boolean underline;
    }
}
