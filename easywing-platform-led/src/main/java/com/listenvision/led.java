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
package com.listenvision;

/**
 * 灵信(ListenVision/TEC) LED 控制卡 SDK 的 JNI 声明。
 * 与官方 demo 保持同名同包，签名逐字对齐，确保能链接到 lv_led_32.dll / lv_led_64.dll。
 *
 * <p>卡型号(LedType):
 * <ul>
 *   <li>0 = T/A/U/XC/W 系列</li>
 *   <li>1 = E 系列(E1,E2)</li>
 *   <li>2 = X 系列</li>
 *   <li>3 = C 系列(C2M,C4M,C2S,C4A)</li>
 *   <li>4 = E5,E6,C8</li>
 *   <li>5 = D2,D4,D6</li>
 * </ul>
 */
public class led {

    // ---------- 颜色常量(BBGGRR) ----------
    /** 红。 */
    public static int COLOR_RED = 0xff;
    /** 绿。 */
    public static int COLOR_GREEN = 0xff00;
    /** 蓝。 */
    public static int COLOR_BLUE = 0xff0000;
    /** 黄。 */
    public static int COLOR_YELLOW = 0xffff;
    /** 青。 */
    public static int COLOR_CYAN = 0xffff00;
    /** 紫。 */
    public static int COLOR_PURPLE = 0xff00ff;
    /** 白。 */
    public static int COLOR_WHITE = 0xffffff;

    // ---------- 定时开关标志 ----------
    /** 启用日期段。 */
    public static int ENABLE_DATE = 0x01;
    /** 启用时间段。 */
    public static int ENABLE_TIME = 0x02;
    /** 启用星期段。 */
    public static int ENABLE_WEEK = 0x04;

    // ---------- 星期标志 ----------
    /** 周日。 */
    public static int WEEK_SUN = 0x01;
    /** 周一。 */
    public static int WEEK_MON = 0x02;
    /** 周二。 */
    public static int WEEK_TUES = 0x04;
    /** 周三。 */
    public static int WEEK_WEN = 0x08;
    /** 周四。 */
    public static int WEEK_THUR = 0x10;
    /** 周五。 */
    public static int WEEK_FRI = 0x20;
    /** 周六。 */
    public static int WEEK_SAT = 0x40;

    // ---------- 初始化 ----------
    /** 初始化卡型号。 */
    public static native void InitLedType(int LedType);

    /** 初始化 RGB。 */
    public static native void InitLedRgb(int Rgb);

    // ---------- 节目 ----------
    /** 创建节目句柄。 */
    public static native long CreateProgram(int LedWidth, int LedHeight, int ColorType, int GrayLevel, int SaveType);

    /** 创建节目句柄（带卡型号）。 */
    public static native long CreateProgram2(
            int LedType, int LedWidth, int LedHeight, int ColorType, int GrayLevel, int SaveType);

    /** 添加节目。 */
    public static native int AddProgram(long hProgram, int ProgramNo, int ProgramTime, int LoopCount);

    /**
     * 设置节目定时。
     *
     * <p>官方 demo 首参为 int（32 位 DLL 导出名 Java_com_listenvision_led_SetProgramTime@76，76 字节=19 个 int 亦佐证）。
     */
    public static native int SetProgramTime(
            int hProgram,
            int ProgramNo,
            int EnableFlag,
            int WeekValue,
            int StartYear,
            int StartMonth,
            int StartDay,
            int StartHour,
            int StartMinute,
            int StartSecond,
            int EndYear,
            int EndMonth,
            int EndDay,
            int EndHour,
            int EndMinute,
            int EndSecond);

    /** 删除节目。 */
    public static native void DeleteProgram(long hProgram);

    // ---------- 区域 ----------
    /** 添加图文区域。 */
    public static native int AddImageTextArea(
            long hProgram, int ProgramNo, int AreaNo, int l, int t, int w, int h, int nLayout);

    /** 添加文件到图文区域。 */
    public static native int AddFileToImageTextArea(
            long hProgram, int ProgramNo, int AreaNo, String FilePath, int InStyle, int nSpeed, int DelayTime);

    /** 添加多行文本（字符串）到图文区域。 */
    public static native int AddMultiLineTextToImageTextArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int InStyle,
            int nSpeed,
            int DelayTime,
            int nAlignment,
            int IsVCenter);

    /** 添加多行文本（带底色/旋转）到图文区域。 */
    public static native int AddMultiLineTextToImageTextAreaBack(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int InStyle,
            int nSpeed,
            int DelayTime,
            int nAlignment,
            int IsVCenter,
            int BackColor,
            int Rotation);

    /** 添加静止文本（字符串）到图文区域。 */
    public static native int AddStaticTextToImageTextArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int DelayTime,
            int nAlignment,
            int IsVCenter);

    /** 添加静止文本（带底色/旋转）到图文区域。 */
    public static native int AddStaticTextToImageTextAreaBack(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int DelayTime,
            int nAlignment,
            int IsVCenter,
            int BackColor,
            int nRotation);

    /** 添加单行文本（字符串）到图文区域。 */
    public static native int AddSinglelineTextToImageTextArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int InStyle,
            int nSpeed,
            int DelayTime);

    /** 添加单行文本（带底色/旋转）到图文区域。 */
    public static native int AddSinglelineTextToImageTextAreaBack(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int AddType,
            String AddStr,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int InStyle,
            int nSpeed,
            int DelayTime,
            int BackColor,
            int Rotation);

    /** 添加数字时钟区域。 */
    public static native int AddDigitalClockArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int l,
            int t,
            int w,
            int h,
            String FontName,
            int FontSize,
            int FontColor,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int IsYear,
            int IsWeek,
            int IsMonth,
            int IsDay,
            int IsHour,
            int IsMinute,
            int IsSecond,
            int DateFormat,
            int DateColor,
            int WeekFormat,
            int WeekColor,
            int TimeFormat,
            int TimeColor,
            int IsMutleLineShow);

    /** 添加计时区域。 */
    public static native int AddTimeArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int l,
            int t,
            int w,
            int h,
            String FontName,
            int FontSize,
            int FontBold,
            int FontItalic,
            int FontUnderline,
            int TimeColor,
            int TxtColor,
            int ShowStyle,
            int IsMutleLineShow,
            String TxtStr,
            int nYear,
            int nMonth,
            int nDay,
            int nHour,
            int nMinute,
            int nSecond,
            int IsShowDay,
            int IsShowHour,
            int IsShowMinute,
            int IsShowSecond);

    /** 添加语音区域。 */
    public static native int AddVoiceArea(
            long hProgram, int ProgramNo, int AreaNo, String VoiceStr, int DelayTime, int PlayCount);

    /** 添加内码区域。 */
    public static native int AddNeiMaArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int l,
            int t,
            int w,
            int h,
            String NeiMaStr,
            int FontSize,
            int FontColor,
            int InStyle,
            int nSpeed,
            int DelayTime);

    /** 添加二维码区域。 */
    public static native int AddQRCodeArea(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int l,
            int t,
            int w,
            int h,
            int LowVersion,
            int HighVersion,
            int ErrLevel,
            int MaskVersion,
            int BlankPointNum,
            int BaseCodeColor,
            int ColorReverse,
            int DefaultShow,
            String defaultCode);

    /** 添加边框。 */
    public static native int AddBorder(
            long hProgram,
            int ProgramNo,
            int AreaNo,
            int l,
            int t,
            int w,
            int h,
            int BorderType,
            int BorderValue,
            int BorderColor,
            int BorderStyle,
            int BorderSpeed);

    // ---------- 发送与控制 ----------
    /** 网络发送。 */
    public static native int NetWorkSend(String IpStr, long hProgram);

    /** 串口发送。 */
    public static native int SerialPortSend(int commport, int baud, int ledNumber, int hProgram);

    /** 内码区刷新。 */
    public static native int RefreshNeiMaArea(String IpStr, String NeiNaStr);

    /** 语音区刷新。 */
    public static native int RefreshVoiceArea(String IpStr, String VoiceStr);

    /** 二维码区刷新。 */
    public static native int RefreshQRCodeArea(String IpStr, String CodeStr);

    /** 设置基本信息。 */
    public static native int SetBasicInfo(String IpStr, int ColorType, int GrayLevel, int LedWidth, int LedHeight);

    /** 设置 OE/DA。 */
    public static native int SetOEDA(String IpStr, int Oe, int Da);

    /** 校时。 */
    public static native int AdjustTime(String IpStr);

    /** 开关屏。 */
    public static native int PowerOnOff(String IpStr, int OnOff);

    /** 定时开关屏。 */
    public static native int TimePowerOnOff(String IpStr, int StartHour, int StartMinute, int EndHour, int EndMinute);

    /** 设置亮度。 */
    public static native int SetBrightness(String IpStr, int BrightnessValue);

    /** 在线检测。 */
    public static native int TestOnline(String IpStr);

    /**
     * 节目切换：ProgramIndex 0-3 播放第 1-4 个节目；>=4 播放默认节目(总数&gt;5 时循环 5..N)。
     * 需先用 LED Player 把卡 IO 设为节目切换。
     */
    public static native int ProgramSwitch(String IpStr, int ProgramIndex);

    /** 启动广域网心跳服务。 */
    public static native int LedInitServer(int port);

    /** 关闭广域网心跳服务。 */
    public static native int LedShudownServer();

    /** 注册广域网心跳回调。 */
    public static native void RegisterLedServerCallback(ledCallBack cb);

    /** 设置通讯参数（改卡 IP 等）。 */
    public static native int SetLedCommunicationParameter(
            String IpStr,
            String NewIp,
            String NewNetMask,
            String NewGateway,
            String NewMac,
            int IsClient,
            String ServerIp,
            int ServerPort,
            int IsDHCP,
            int IsHeartBeat,
            int HeartBeatTime);

    /** 设置服务端信息。 */
    public static native int SetLedServerInfo(
            String IpStr, int IsClient, String ServerIp, int ServerPort, int HeartBeatTime);

    /** 查询错误码描述。 */
    public static native String GetErrorCodeInfo(int nErrCode);

    /** 设置继电器。 */
    public static native int SetRelay(
            String IpStr,
            int type,
            int manualSwitch,
            int flag1,
            String startTime1,
            String endTime1,
            int flag2,
            String startTime2,
            String endTime2,
            int flag3,
            String startTime3,
            String endTime3);

    /** 定时亮度（3 组）。 */
    public static native int TimeBrightness(
            String Ip,
            int StartHour1,
            int StartMinute1,
            int EndHour1,
            int EndMinute1,
            int BriValue1,
            int StartHour2,
            int StartMinute2,
            int EndHour2,
            int EndMinute2,
            int BriValue2,
            int StartHour3,
            int StartMinute3,
            int EndHour3,
            int EndMinute3,
            int BriValue3);

    /** 测试。 */
    public static native int LedTest(String IpStr, int TestValue);
}
