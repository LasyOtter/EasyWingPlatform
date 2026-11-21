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
package cn.com.listentech;

/**
 * 灵信五代单双色 V2014 动态库 JNI 声明（{@code ListenPlayDll.dll}，64 位）。
 *
 * <p>与官方 64 位 {@code LEDControl.java} 声明一一对应；中文注释从官方文件继承，
 * 因原文件为 GBK 编码，此处仅保留可读的中文说明，签名严格照抄。
 *
 * <p>调用模型（会话句柄）：
 * <pre>
 *   StartSend() → SetTransMode(...) → SetNetworkPara/SetSerialPortPara
 *     → AddControl / AddProgram / SetProgramTimer / AddXxx 区域
 *     → SendControl(...) → EndSend(...)
 * </pre>
 */
public class LEDControl {

    /**
     * 库文件绝对路径，由 {@code ListenSdk} 在加载前通过
     * {@code -Dledsystem.listen.dll=<绝对路径>} 传入。
     *
     * <p>Windows 的 {@code System.loadLibrary("ListenPlayDll")} 只会按
     * {@code java.library.path} 搜索短名，不会搜索 DLL 所在目录，
     * 因此这里优先用绝对路径 {@code System.load}，彻底摆脱 library path 依赖；
     * 未指定绝对路径时回退到 {@code loadLibrary}。
     */
    static {
        String abs = System.getProperty("ledsystem.listen.dll");
        if (abs != null && !abs.isBlank()) {
            System.load(abs);
        } else {
            System.loadLibrary("ListenPlayDll");
        }
    }

    // ================= 通讯会话 =================

    /** 启动通讯会话，返回会话句柄。 */
    public static native int StartSend();

    /** 结束通讯会话。返回 1 成功 / 2 不成功。 */
    public static native int EndSend(int handle);

    /**
     * 设置通讯模式。
     *
     * @param handle 会话句柄
     * @param TransMode 1 网络通讯，2 串口通讯
     * @param markrf 保留，官方 demo 传 1
     * @param controlType 控制器型号（T1 Net/T2 Net/D2/D3/D4/D5 等）
     * @param pno 屏号
     * @return 1 成功 / 2 不成功
     */
    public static native int SetTransMode(int handle, int TransMode, int markrf, int controlType, int pno);

    /** 设置网络参数：屏号、IP、端口。返回 1 成功 / 2 不成功。 */
    public static native int SetNetworkPara(int handle, int pno, String ip, int port);

    /** 设置串口参数：屏号、串口号、波特率。返回 1 成功 / 2 不成功。 */
    public static native int SetSerialPortPara(int handle, int pno, int comno, int baud);

    // ================= 屏 / 节目 =================

    /** 添加显示屏：屏号、基色（1 单色 2 双色 3 三基色）。 */
    public static native int AddControl(int handle, int pno, int DBColor);

    /** 添加节目：节目号、播放时间。 */
    public static native int AddProgram(int handle, int jno, int playTime);

    /**
     * 设置节目定时。
     *
     * <p>注意：官方 64 位 Java 声明含 pno（与文档文本不一致），此处以 Java 声明为准。
     */
    public static native int SetProgramTimer(
            int handle,
            int pno,
            int jno,
            int TimingModel,
            int WeekSelect,
            int startSecond,
            int startMinute,
            int startHour,
            int startDay,
            int startMonth,
            int startWeek,
            int startYear,
            int endSecond,
            int endMinute,
            int endHour,
            int endDay,
            int endMonth,
            int endWeek,
            int endYear);

    // ================= 文本 / 图文区域 =================

    /** 添加静止文本。 */
    public static native int AddQuitText(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            int FontColor,
            String fontName,
            int fontSize,
            boolean fontBold,
            boolean Italic,
            boolean Underline,
            String text);

    /** 添加文件区域（后续用 AddFile 追加文件）。 */
    public static native int AddFileArea(
            int handle, int jno, int qno, int left, int top, int width, int height, int BackBit);

    /** 添加文件到图文区域。 */
    public static native int AddFile(
            int handle,
            int jno,
            int qno,
            int mno,
            String fileName,
            int width,
            int height,
            int playstyle,
            int QuitStyle,
            int playspeed,
            int delay,
            int MidText);

    /** 添加字符串到图文区域（多行）。 */
    public static native int AddFileString(
            int handle,
            int jno,
            int qno,
            int mno,
            String str,
            String fontname,
            int fontsize,
            int fontcolor,
            boolean bold,
            boolean italic,
            boolean underline,
            int align,
            int width,
            int height,
            int playstyle,
            int QuitStyle,
            int playspeed,
            int delay,
            int MidText);

    /** 添加单行文本（字符串）。 */
    public static native int AddLnTxtString(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            String str,
            String fontname,
            int fontsize,
            int fontcolor,
            boolean bold,
            boolean italic,
            boolean underline,
            int PlayStyle,
            int Playspeed,
            int times);

    /** 添加单行文本（rtf 文件）。 */
    public static native int AddLnTxtArea(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            String LnFileName,
            int PlayStyle,
            int Playspeed,
            int times);

    // ================= 时钟 / 计时 / 内码 =================

    /** 添加数字时钟。 */
    public static native int AddDClockArea(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            int fontColor,
            String fontName,
            int fontSize,
            int fontBold,
            int Italic,
            int Underline,
            int year,
            int week,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int TwoOrFourYear,
            int HourShow,
            int format,
            int spanMode,
            int Advacehour,
            int Advaceminute);

    /** 添加计时区（正计时/倒计时）。 */
    public static native int AddTimerArea(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            int fontColor,
            String fontName,
            int fontSize,
            int fontBold,
            int italic,
            int underline,
            int mode,
            int dayshow,
            int culweek,
            int culday,
            int culhour,
            int culmin,
            int culse,
            int year,
            int week,
            int month,
            int day,
            int hour,
            int minute,
            int second);

    /**
     * 添加内码区。
     *
     * <p>ShowStyle 点阵 16/32；fontname 字体索引 0=宋体；fontcolor 1 红 2 绿 3 黄。
     */
    public static native int AddNeiMaTxtArea(
            int handle,
            int jno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            String showtext,
            int ShowStyle,
            int fontname,
            int fontcolor,
            int fontsize,
            int PlayStyle,
            int QuitStyle,
            int Playspeed,
            int times);

    /** 添加流动边框。 */
    public static native int AddWaterFrame(
            int handle,
            int pno,
            int prgramno,
            int qno,
            int left,
            int top,
            int width,
            int height,
            int FrameID,
            int FrameWidth,
            int FrameStyle,
            int FrameMode,
            int FrameStep,
            int FrameSpeed,
            int FrameColor,
            int DispalyMode);

    // ================= 发送 / 控制 =================

    /** 发送数据。SendType 1 普通 2 SD 卡；hwnd 一般取 0。返回 0/1/2/3。 */
    public static native int SendControl(int handle, int SendType, int hwd);

    /** 手动校正时间。返回 1 成功 2 通讯失败 3 发送失败。 */
    public static native int AdjustTime(int handle, int wnd);

    /** 开关屏：0 开屏 1 关屏。 */
    public static native int SetPower(int handle, int power);

    /** 设置亮度 1-16。 */
    public static native int SetBrightness(int handle, int value);

    /** 定时亮度（3 组）。 */
    public static native int SetBrightnessTimer(
            int handle,
            int bTimer,
            int startHour1,
            int startMinute1,
            int endHour1,
            int endMinute1,
            int brightness1,
            int startHour2,
            int startMinute2,
            int endHour2,
            int endMinute2,
            int brightness2,
            int startHour3,
            int startMinute3,
            int endHour3,
            int endMinute3,
            int brightness3);

    /** 定时开关机（3 组）。 */
    public static native int SetPowerTimer(
            int handle,
            int bTimer,
            int startHour1,
            int startMinute1,
            int endHour1,
            int endMinute1,
            int startHour2,
            int startMinute2,
            int endHour2,
            int endMinute2,
            int startHour3,
            int startMinute3,
            int endHour3,
            int endMinute3,
            int hwnd);

    /** 搜索控制器，结果写入 ini 文件。 */
    public static native int SearchController(int handle, String filePath, boolean broadcast, int value);

    /** 设置屏参：基色、宽度、高度。 */
    public static native int SendScreenPara(int handle, int DBColor, int width, int height);

    /** 设置屏参结构体。 */
    public static native int SetScreenInfo(int handle, ScreenInfo screeninfo);

    /** 设置模组参数（E 系列 OE/DA 等）。 */
    public static native int SetModuleInfo(int handle, ModuleInfo moduleinfo);

    /** 设置扫描参数。 */
    public static native int SetScanLineInfo(int handle, ScanLineInfo scanlineinfo);

    /** 硬件参数（镜像/扫描/线序/消隐/红绿交换/张空）。 */
    public static native int SetHardPara(
            int handle, int Mirror, int ScanStyle, int LineOrder, int cls, int RGChange, int zhangKong, int hwnd);

    /** 远程网络参数（MAC/IP/网关/掩码）。 */
    public static native int SetRemoteNetwork(
            int handle,
            String macAddress,
            String ip,
            String gateway,
            String subnetmask,
            String newmac,
            String newip,
            String newgateway,
            String newsubnetmask);

    /** 定时限制（禁用某时间段）。 */
    public static native int SetTimingLimit(
            int handle,
            int FSecond,
            int FMinute,
            int FHour,
            int FDay,
            int FMonth,
            int FWeek,
            int FYear,
            int compo,
            boolean enable);

    /** 取消定时限制。 */
    public static native int CancelTimingLimit(int handle);

    /** 测试开关。 */
    public static native int SetTest(int handle, int value);

    /** 磁盘名设置。 */
    public static native int SetOrderPara(int handle, String diskName);
}
