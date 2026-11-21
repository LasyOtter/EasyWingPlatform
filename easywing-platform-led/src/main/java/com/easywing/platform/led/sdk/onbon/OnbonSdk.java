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
package com.easywing.platform.led.sdk.onbon;

import com.easywing.platform.led.config.Area;
import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Program;
import com.easywing.platform.led.config.Rect;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.engine.Colors;
import com.easywing.platform.led.engine.ProgramBuilder;
import com.easywing.platform.led.sdk.LedException;
import com.easywing.platform.led.sdk.LedSdk;
import java.awt.Color;
import java.awt.Font;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import onbon.bx06.Bx6GBrightnessClockEnv;
import onbon.bx06.Bx6GEnv;
import onbon.bx06.Bx6GScreen;
import onbon.bx06.Bx6GScreenClient;
import onbon.bx06.Bx6GScreenProfile;
import onbon.bx06.Bx6GScreenRS;
import onbon.bx06.area.BxArea;
import onbon.bx06.area.CounterBxArea;
import onbon.bx06.area.DateTimeBxArea;
import onbon.bx06.area.DynamicBxArea;
import onbon.bx06.area.SoundBxArea;
import onbon.bx06.area.TextCaptionBxArea;
import onbon.bx06.area.page.ImageFileBxPage;
import onbon.bx06.area.page.TextBxPage;
import onbon.bx06.area.unit.CounterBxUnit.CounterBxUnitFormat;
import onbon.bx06.area.unit.CounterBxUnit.CounterBxUnitMode;
import onbon.bx06.cmd.dyn.DynamicBxAreaRule;
import onbon.bx06.cmd.led.ScreenTimingOnOffCmd;
import onbon.bx06.file.ProgramBxFile;
import onbon.bx06.file.ProgramBxFile.ProgramWeek;
import onbon.bx06.series.Bx6Card;
import onbon.bx06.series.Bx6E;
import onbon.bx06.series.Bx6M;
import onbon.bx06.series.Bx6Q;
import onbon.bx06.series.Bx6X;
import onbon.bx06.utils.DisplayStyleFactory;
import onbon.bx06.utils.DisplayStyleFactory.DisplayStyle;
import onbon.bx06.utils.TextBinary.Alignment;
import org.springframework.stereotype.Component;

/**
 * 仰邦（BX / onbon）SDK 适配，基于官方纯 Java 库 {@code onbon.bx06}（socket，无 native 依赖）。
 *
 * <p>与灵信的「句柄式建节目 → 整屏下发」不同，BX 的模型是
 * {@code Bx6GScreenClient}（TCP 客户端）→ {@code ProgramBxFile}（节目）→ {@code writeProgram}：
 * <pre>
 *   Bx6GEnv.initial(timeout)               // 进程内一次
 *   new Bx6GScreenClient(id, card) → connect(ip, 5005)
 *   new ProgramBxFile(name, profile) → addArea(BxArea) → writeProgram(pf)
 * </pre>
 *
 * <p><b>能力</b>：整屏下发（含单节目下发）+ 动态区实时刷新（内码区映射为 BX 动态区）+ 在线检测
 * + 开关屏 / 校正时间 / 亮度 / 定时亮度 / 定时开关机。
 *
 * <p><b>语义差异与限制</b>（调用方需注意）：
 * <ul>
 *   <li>{@link #switchProgram} 抛 {@link LedException}：BX 没有「切到某节目」的直接 API，
 *       官方 {@code lock/unlock} 是锁定/解锁「当前画面」，并非节目切换。</li>
 *   <li>BX 无独立二维码 / 边框区域类，配置里的 {@code qrcode}/{@code border} 区域会跳过并告警。</li>
 *   <li>内码区（{@code type: neima}）随节目下发时用 {@code DynamicBxArea} 呈现，
 *       实时刷新（%disp 帧）走 {@link #refreshNeima} → {@code writeDynamic}。
 *       {@code areaIndex} 对应 {@link DynamicBxAreaRule#setId(int)}，刷新帧的「区域编号」由
 *       {@code %disp} 帧解析得到。</li>
 *   <li>{@code Screen.colorType}（1/2/3/4）在 {@code connect(ip, port)} 后由控制器应答
 *       重建 {@code Bx6GScreenProfile}（颜色类型 / 矩阵类型），适配层不覆写——
 *       实测配置值与实际模组不符时以控制器为准，避免下发错误屏参。</li>
 *   <li>卡型号由 {@code Screen.ledType} 推断（1→E、2→X、5→Q、其余→M）。</li>
 *   <li><b>屏参/扫描参数</b>（{@code screen.scan}）不自动下发。已评估后决定不做：
 *       {@code ScanConfigBxFile} 的字段（chipID/conTableData/empPointTableData/crc16 等）
 *       是芯片走线表，无法从 {@code ScanTuning} 推导；{@code readScan()} 只回裸字节流、
 *       SDK 无反序列化方法，唯一可行路径是对字节流定点修补，依赖未公开的二进制布局，
 *       花屏风险高且不可维护。官方文档亦要求先用 LedshowTW 完成扫描/屏参配置，
 *       二次开发只负责节目与动态区。</li>
 *   <li><b>星期位序</b>：项目约定周日=0x01（灵信），BX 约定周日=0x80。
 *       {@link #applySchedule} 采用「逐字段赋值」而非整字节透传，因此跨厂商位序差异被自然消除。</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public final class OnbonSdk implements LedSdk {

    /** 六代（BX-6E 等）网络默认端口。 */
    private static final int DEFAULT_TCP_PORT = 5005;
    /** Bx6GEnv.initial 的超时（毫秒）。 */
    private static final int INIT_TIMEOUT_MS = 30000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 动态区（RAM）内可创建的最大动态区个数（BX-6E/6Q 为 32，BX-5E 为 4）。 */
    private static final int MAX_DYNAMIC_AREAS = 32;

    /** 动态区刷新规则：与全部节目同播、循环、立即播放。 */
    private static final byte DYNAMIC_RUN_MODE_CYCLE = 0;

    private static final byte DYNAMIC_IMMEDIATE_PLAY_WITH_PROGRAMS = 0;

    private volatile boolean initialized;

    @Override
    public String provider() {
        return "onbon";
    }

    @Override
    public synchronized void init(LedConfig config) {
        if (initialized) {
            return;
        }
        try {
            Bx6GEnv.initial(INIT_TIMEOUT_MS);
        } catch (Exception e) {
            throw new LedException("Bx6GEnv.initial", -1, "仰邦 SDK 初始化失败: " + e.getMessage(), e);
        }
        initialized = true;
        log.info("[SDK] 仰邦 bx06 就绪 ({})", Bx6GEnv.VER_INFO);
    }

    @Override
    public boolean initialized() {
        return initialized;
    }

    // ==================== 下发 ====================

    @Override
    public ProgramBuilder.DeployResult deploy(Screen screen, LedConfig config) {
        return deployPrograms(screen, config, null);
    }

    @Override
    public ProgramBuilder.DeployResult deployProgram(Screen screen, LedConfig config, int programNo) {
        Program target = findProgram(screen, programNo);
        if (target == null) {
            throw new LedException("deployProgram", -3, "节目不存在: " + programNo);
        }
        return deployPrograms(screen, config, target);
    }

    /** 下发一个或多个节目（only 为 null 表示全部）。 */
    private ProgramBuilder.DeployResult deployPrograms(Screen screen, LedConfig config, Program only) {
        int ok = 0;
        int failed = 0;
        String lastError = null;

        try (BxConnection conn = open(screen)) {
            List<Program> programs = new ArrayList<>();
            if (only != null) {
                programs.add(only);
            } else {
                programs.addAll(screen.getPrograms());
            }

            for (Program program : programs) {
                try {
                    ProgramBxFile pf = buildProgram(screen, program, conn);
                    if (!conn.screen().writeProgram(pf)) {
                        throw new LedException(
                                "WriteProgram", -1, "屏拒绝节目 " + program.getProgramNo() + "（writeProgram 返回 false）");
                    }
                    // 动态区（RAM，独立于节目）必须显式创建：DynamicBxArea 只提供几何与内容模板，
                    // 节目文件序列化的是 DynamicArea 报文（不含 areaId），无法建立动态区 ID 规则。
                    createDynamicAreas(screen, program, conn);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    lastError = e.getMessage();
                    log.warn("[部署失败] {} 节目 {}: {}", screen.getId(), program.getProgramNo(), e.getMessage());
                }
            }
        } catch (Exception e) {
            failed++;
            lastError = e.getMessage();
            log.warn("[部署失败] {}: {}", screen.getId(), e.getMessage());
        }
        return new ProgramBuilder.DeployResult(ok, failed, lastError);
    }

    /** 构建单个节目文件（区域翻译 + 节目定时）。 */
    private ProgramBxFile buildProgram(Screen screen, Program program, BxConnection conn) throws Exception {
        // 必须用 int 构造器：String 构造器强制校验 "PXXX"，programNo=0 会抛 IllegalArgumentException。
        // int 构造器内部经 FileType.PROGRAM.create(int) 生成 P000..P999（0-255 → P000..P255）。
        ProgramBxFile pf = new ProgramBxFile(program.getProgramNo(), conn.profile());
        pf.setProgramPlayTimes(program.getLoopCount());
        if (program.getPlayTime() > 0) {
            pf.setProgramTimeSpan(program.getPlayTime());
        }
        applySchedule(pf, program.getSchedule());

        int neimaSeq = 0;
        for (Area area : program.getAreas()) {
            BxArea bx = buildArea(screen, area, conn.profile(), neimaSeq);
            if (bx != null) {
                pf.addArea(bx);
            }
            if (isNeima(area)) {
                neimaSeq++;
            }
        }
        return pf;
    }

    /**
     * 为刷新用的内码区创建动态区。
     *
     * <p>动态区 ID 与 {@code %disp} 帧的「区域编号」一一对应：{@code neimaIndex} 显式指定时用其值，
     * 否则按内码区在节目内的出现顺序分配 0 起（与 {@code ProgramBuilder.neimaAreaChar} 一致）。
     */
    private void createDynamicAreas(Screen screen, Program program, BxConnection conn) {
        int seq = 0;
        for (Area area : program.getAreas()) {
            if (!isNeima(area)) {
                continue;
            }
            int id = area.getNeimaIndex() != null ? area.getNeimaIndex() : seq;
            seq++;
            if (id < 0 || id >= MAX_DYNAMIC_AREAS) {
                log.warn("[警告] 屏 {} 内码区顺序号 {} 超出动态区范围(0-{})，已跳过", screen.getId(), id, MAX_DYNAMIC_AREAS - 1);
                continue;
            }
            Rect r = rectOf(area, screen);
            DynamicBxAreaRule rule = new DynamicBxAreaRule();
            rule.setId(id);
            rule.setRunMode(DYNAMIC_RUN_MODE_CYCLE);
            rule.setImmediatePlay(DYNAMIC_IMMEDIATE_PLAY_WITH_PROGRAMS);
            rule.setRelativeAllPrograms(true);

            DynamicBxArea dArea =
                    new DynamicBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), conn.profile());
            dArea.setFont(toAwtFont(area.effectiveFont()));
            dArea.addPage(textPage(area));
            Bx6GScreen.Result<?> result = conn.screen().writeDynamic(rule, dArea);
            if (result == null || !result.isOK()) {
                log.warn("[警告] 屏 {} 动态区 {} 创建失败: {}", screen.getId(), id, result == null ? "no reply" : result);
            }
        }
    }

    // ==================== 区域翻译 ====================

    /** 配置区域 → BX 区域；不支持的类型返回 null（调用方记录告警）。 */
    private BxArea buildArea(Screen screen, Area a, Bx6GScreenProfile profile, int neimaSeq) {
        Rect r = rectOf(a, screen);
        String type = a.getType() == null ? "" : a.getType().toLowerCase(Locale.ROOT);

        switch (type) {
            case "static":
            case "singleline":
            case "multiline": {
                TextCaptionBxArea area =
                        new TextCaptionBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), profile);
                area.setFont(toAwtFont(a.effectiveFont()));
                area.addPage(textPage(a));
                return area;
            }
            case "neima": {
                // 内码区仅用于刷新落点几何：DynamicBxArea 模板由 createDynamicAreas 通过
                // writeDynamic 注册（ID 规则在此建立）；节目文件里再放一份动态区是冗余的
                // （DynamicArea 报文不含 areaId，且同 ID 规则重复注册）。故此处跳过。
                return null;
            }
            case "image": {
                TextCaptionBxArea area =
                        new TextCaptionBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), profile);
                String path = a.getFilePath() == null ? "" : a.getFilePath();
                area.addPage(new ImageFileBxPage(path));
                return area;
            }
            case "voice": {
                SoundBxArea area = new SoundBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), profile);
                area.setContent(a.getContent() == null ? "" : a.getContent());
                area.setVolume(50);
                area.setSpeed(50);
                area.setRepeatTimes(a.getPlayCount() != null ? a.getPlayCount() : 1);
                area.setEncoding(SoundBxArea.EncodingType.GBK);
                return area;
            }
            case "clock": {
                DateTimeBxArea area = new DateTimeBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), profile);
                area.setFont(toAwtFont(a.effectiveFont()));
                area.setForeground(toAwtColor(a.effectiveFont().getColor()));
                area.setDateStyle(onbon.bx06.area.DateStyle.YYYY_MM_DD_1);
                area.setWeekStyle(weekStyle(a.getWeekFormat()));
                area.setTimeStyle(timeStyle(a.getTimeFormat()));
                area.setMultiline(a.getMultiLine() != null && a.getMultiLine() == 1);
                return area;
            }
            case "time": {
                return buildTimeArea(a, r, profile);
            }
            case "qrcode":
            case "border":
                log.warn("[警告] 屏 {} 区域类型 {} 在仰邦 SDK 中无对应区域类，已跳过", screen.getId(), a.getType());
                return null;
            default:
                log.warn("[警告] 屏 {} 不支持的区域类型: {}，已跳过", screen.getId(), a.getType());
                return null;
        }
    }

    private BxArea buildTimeArea(Area a, Rect r, Bx6GScreenProfile profile) {
        LocalDateTime end =
                a.getEndTime() != null ? LocalDateTime.parse(a.getEndTime(), DATE_TIME) : LocalDateTime.now();
        Date dest = new Date(java.sql.Timestamp.valueOf(end).getTime());
        CounterBxArea area = new CounterBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), dest, profile);
        area.setUnitColor(toAwtColor(a.effectiveFont().getColor()));
        // countMode：1 正计 / 2 倒计（默认 2）。BX 的 A/B/C/D = 正/反 × 累加/不累加；
        // 倒计时到点后停止更贴近「倒计时」语义，故取 D（反计时不累加）；正计时取 A（累加）。
        boolean forward = a.getCountMode() != null && a.getCountMode() == 1;
        area.setMode(forward ? CounterBxUnitMode.A : CounterBxUnitMode.D);
        area.setFormat(new CounterBxUnitFormat(
                nvl(a.getShowDay()), nvl(a.getShowHour()), nvl(a.getShowMinute()), nvl(a.getShowSecond())));
        return area;
    }

    private TextBxPage textPage(Area a) {
        TextBxPage page = new TextBxPage(a.getContent() == null ? "" : a.getContent());
        Area.Font f = a.effectiveFont();
        page.setFont(toAwtFont(f));
        page.setForeground(toAwtColor(f.getColor()));
        if (a.getAlignment() != null) {
            page.setHorizontalAlignment(toAlignment(a.getAlignment()));
        }
        if (a.getInStyle() > 0) {
            DisplayStyle style = DisplayStyleFactory.getStyle(a.getInStyle());
            if (style != null) {
                page.setDisplayStyle(style);
            } else {
                log.warn("[警告] 未找到显示特技 {}，按默认处理", a.getInStyle());
            }
        }
        if (a.getSpeed() > 0) {
            page.setSpeed(a.getSpeed());
        }
        if (a.getDelay() > 0) {
            page.setStayTime(a.getDelay());
        }
        return page;
    }

    private static Alignment toAlignment(int alignment) {
        return switch (alignment) {
            case 1 -> Alignment.FAR;
            case 2 -> Alignment.CENTER;
            default -> Alignment.NEAR;
        };
    }

    // ==================== 控制指令 ====================

    @Override
    public void switchProgram(Screen screen, int programIndex) {
        // 仰邦 SDK 无「切换到指定节目」接口；lockProgram 是锁定节目播放顺序，并非节目切换。
        throw new LedException(
                "SwitchProgram(" + screen.getIp() + ", " + programIndex + ")", -1, provider() + " 不支持节目切换（BX 无对应接口）");
    }

    @Override
    public boolean isOnline(Screen screen) {
        try (BxConnection conn = open(screen)) {
            Bx6GScreen.Result<?> result = conn.screen().ping();
            return result != null && result.isOK();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void adjustTime(Screen screen, LedConfig config) {
        try (BxConnection conn = open(screen)) {
            requireOk(conn.screen().syncTime(), "SyncTime");
        } catch (Exception e) {
            throw asLedException("AdjustTime(" + screen.getIp() + ")", e);
        }
    }

    @Override
    public void setPower(Screen screen, LedConfig config, boolean on) {
        try (BxConnection conn = open(screen)) {
            requireOk(on ? conn.screen().turnOn() : conn.screen().turnOff(), on ? "TurnOn" : "TurnOff");
        } catch (Exception e) {
            throw asLedException("SetPower(" + screen.getIp() + ")", e);
        }
    }

    @Override
    public void setBrightness(Screen screen, LedConfig config, int value) {
        int v = Math.max(1, Math.min(16, value));
        try (BxConnection conn = open(screen)) {
            requireOk(conn.screen().manualBrightness((byte) v), "ManualBrightness");
        } catch (Exception e) {
            throw asLedException("SetBrightness(" + screen.getIp() + ")", e);
        }
    }

    /**
     * 定时亮度：把「时段 → 亮度」映射到 BX 的 48 段半小时亮度表。
     *
     * <p>配置组为 5 元 {@code {startHour, startMinute, endHour, endMinute, brightness}}，
     * 最多 3 组；未配置的半小时槽位保持 SDK 默认值 15（最亮）。
     * {@code Bx6GBrightnessClockEnv.setup(halfHour, level)}：
     * {@code halfHour} = 半小时索引 0(00:00-00:29)…47(23:30-23:59)，{@code level} = 1-16。
     * 实现内部 clamp 后存 {@code level-1} 到 {@code levels[idx]}。
     */
    @Override
    public void setBrightnessTimer(Screen screen, LedConfig config, List<int[]> groups, List<Integer> brightness) {
        Bx6GBrightnessClockEnv env = new Bx6GBrightnessClockEnv();
        if (groups == null || groups.isEmpty()) {
            throw new LedException("SetBrightnessTimer(" + screen.getIp() + ")", -1, "定时亮度组为空");
        }
        int n = Math.min(groups.size(), 3);
        for (int i = 0; i < n; i++) {
            int[] g = groups.get(i);
            if (g == null || g.length < 5) {
                log.warn("[警告] 定时亮度组 {} 需要 5 元 {{startHour,startMinute,endHour,endMinute,brightness}}，已跳过", i);
                continue;
            }
            int b = (brightness != null && i < brightness.size() && brightness.get(i) != null) ? brightness.get(i) : 1;
            int from = halfHourOf(g[0], g[1]);
            int to = halfHourOf(g[2], g[3]);
            if (from < 0 || to < 0 || from > to) {
                log.warn("[警告] 定时亮度组 {} 时段非法: {}:{}-{}:{}，已跳过", i, g[0], g[1], g[2], g[3]);
                continue;
            }
            // 闭区间 [from, to] 内每个半小时槽位设为该亮度
            for (int h = from; h <= to; h++) {
                env.setup(h, b);
            }
        }
        try (BxConnection conn = open(screen)) {
            requireOk(conn.screen().clockBrightness(env), "ClockBrightness");
        } catch (Exception e) {
            throw asLedException("SetBrightnessTimer(" + screen.getIp() + ")", e);
        }
    }

    /** 小时/分钟 → 半小时索引（0-47）；非法返回 -1。 */
    private static int halfHourOf(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 2 + (minute >= 30 ? 1 : 0);
    }

    /**
     * 定时开关机：4 元 {@code {onHour, onMinute, offHour, offMinute}}，最多 3 组。
     *
     * <p>BX 的 {@code ScreenTimingOnOffCmd.addTime} 只能向控制器追加时段，无法覆盖已有组；
     * 当配置为空（表示清除定时）时先 {@code cancelTimingOnOff()}，否则按「增量」语义追加。
     */
    @Override
    public void setPowerTimer(Screen screen, LedConfig config, List<int[]> groups) {
        ScreenTimingOnOffCmd cmd = new ScreenTimingOnOffCmd();
        int n = groups == null ? 0 : Math.min(groups.size(), 3);
        for (int i = 0; i < n; i++) {
            int[] g = groups.get(i);
            if (g == null || g.length < 4) {
                log.warn("[警告] 定时开关机组 {} 需要 4 元 {{onHour,onMinute,offHour,offMinute}}，已跳过", i);
                continue;
            }
            // addTime(onHour, onMinute, offHour, offMinute)
            cmd.addTime(g[0], g[1], g[2], g[3]);
        }
        try (BxConnection conn = open(screen)) {
            if (n == 0) {
                requireOk(conn.screen().cancelTimingOnOff(), "CancelTimingOnOff");
                return;
            }
            requireOk(conn.screen().setupTimingOnOff(cmd), "SetupTimingOnOff");
        } catch (Exception e) {
            throw asLedException("SetPowerTimer(" + screen.getIp() + ")", e);
        }
    }

    // ==================== 内码实时刷新 ====================

    @Override
    public boolean supportsNeimaRefresh() {
        return true;
    }

    /**
     * 下发一帧 %disp 刷新文本：按区域编号切分为多个动态区，逐个 writeDynamic。
     *
     * <p>帧格式：{@code %disp + 区域编号(ASCII '0'+index) + 颜色符号 + 特效(0-9) + 内容}，
     * 多区域拼接。颜色符号 {@code :}红 {@code ;}绿 {@code <}黄 {@code =}紫 {@code >}蓝 {@code ?}淡 {@code @}白。
     */
    @Override
    public void refreshNeima(Screen screen, String frame) {
        if (frame == null || frame.isEmpty()) {
            return;
        }
        try (BxConnection conn = open(screen)) {
            List<DynamicSegment> segments = parseNeimaFrame(frame);
            for (DynamicSegment seg : segments) {
                // 单段失败不拖垮整帧：与 ProgramBuilder.neimaFrame 的容错策略一致，
                // 跳过该区域，其余区域照常刷新。
                try {
                    DynamicBxAreaRule rule = new DynamicBxAreaRule();
                    rule.setId(seg.areaIndex);
                    rule.setRunMode(DYNAMIC_RUN_MODE_CYCLE);
                    rule.setImmediatePlay(DYNAMIC_IMMEDIATE_PLAY_WITH_PROGRAMS);
                    rule.setRelativeAllPrograms(true);

                    DynamicBxArea area = dynamicAreaTemplate(conn.profile(), screen, seg.areaIndex);
                    TextBxPage page = new TextBxPage(seg.text);
                    page.setFont(areaFont(area));
                    page.setForeground(toAwtColor(seg.colorName));
                    if (seg.effect > 0) {
                        DisplayStyle style = DisplayStyleFactory.getStyle(seg.effect);
                        if (style != null) {
                            page.setDisplayStyle(style);
                        }
                    }
                    area.clearPages();
                    area.addPage(page);
                    Bx6GScreen.Result<?> result = conn.screen().writeDynamic(rule, area);
                    if (result == null || !result.isOK()) {
                        log.warn(
                                "[刷新] 屏 {} 动态区 {} 刷新失败: {}",
                                screen.getId(),
                                seg.areaIndex,
                                result == null ? "no reply" : result);
                    }
                } catch (Exception ex) {
                    log.warn("[刷新] 屏 {} 动态区 {} 刷新异常，已跳过: {}", screen.getId(), seg.areaIndex, ex.getMessage());
                }
            }
        } catch (Exception e) {
            throw asLedException("RefreshNeima(" + screen.getIp() + ")", e);
        }
    }

    /** 刷新分段（一个动态区一段）。 */
    private static final class DynamicSegment {
        private int areaIndex;
        private String colorName = "green";
        private int effect;
        private String text = "";
    }

    /** 解析 %disp 帧为多段动态区刷新。 */
    private static List<DynamicSegment> parseNeimaFrame(String frame) {
        List<DynamicSegment> segments = new ArrayList<>();
        int i = 0;
        while (i < frame.length()) {
            int disp = frame.indexOf("%disp", i);
            if (disp < 0) {
                break;
            }
            int p = disp + 5;
            if (p >= frame.length()) {
                break;
            }
            DynamicSegment seg = new DynamicSegment();
            seg.areaIndex = frame.charAt(p) - '0';
            p++;
            if (p < frame.length()) {
                seg.colorName = colorNameOfSymbol(frame.charAt(p));
                p++;
            }
            if (p < frame.length()) {
                seg.effect = frame.charAt(p) - '0';
                p++;
            }
            int next = frame.indexOf("%disp", p);
            seg.text = next < 0 ? frame.substring(p) : frame.substring(p, next);
            segments.add(seg);
            i = next < 0 ? frame.length() : next;
        }
        if (segments.isEmpty()) {
            // 非 %disp 帧：整体作为 0 号区域刷新
            DynamicSegment seg = new DynamicSegment();
            seg.text = frame;
            segments.add(seg);
        }
        return segments;
    }

    private static String colorNameOfSymbol(char symbol) {
        return switch (symbol) {
            case ':' -> "red";
            case ';' -> "green";
            case '<' -> "yellow";
            case '=' -> "purple";
            case '>' -> "blue";
            case '?' -> "light";
            case '@' -> "white";
            default -> "green";
        };
    }

    /** 取动态区模板（尺寸沿用节目内同 ID 的内码区；找不到则铺满屏）。 */
    private DynamicBxArea dynamicAreaTemplate(Bx6GScreenProfile profile, Screen screen, int areaIndex) {
        Rect r = dynamicRect(screen, areaIndex);
        return new DynamicBxArea(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), profile);
    }

    private Rect dynamicRect(Screen screen, int areaIndex) {
        for (Program p : screen.getPrograms()) {
            int seq = 0;
            for (Area a : p.getAreas()) {
                if (!isNeima(a)) {
                    continue;
                }
                int id = a.getNeimaIndex() != null ? a.getNeimaIndex() : seq;
                if (id == areaIndex) {
                    return rectOf(a, screen);
                }
                seq++;
            }
        }
        return new Rect(0, 0, screen.getWidth(), screen.getHeight());
    }

    // ==================== 连接与配置映射 ====================

    /** 打开到控制卡的连接（TCP 或串口），并应用屏参微调。 */
    private BxConnection open(Screen screen) throws Exception {
        if (!initialized) {
            throw new LedException("Open(" + screen.getIp() + ")", -1, "仰邦 SDK 未初始化");
        }
        String conn =
                screen.getConnection() == null ? "tcp" : screen.getConnection().toLowerCase(Locale.ROOT);
        if ("serial".equals(conn)) {
            return openSerial(screen);
        }
        Bx6GScreenClient client = new Bx6GScreenClient(screen.getId(), cardOf(screen));
        boolean connected = client.connect(screen.getIp(), DEFAULT_TCP_PORT);
        if (!connected) {
            throw new LedException("Connect(" + screen.getIp() + ")", -1, "连接控制卡失败");
        }
        applyTuning(client, screen);
        return new BxConnection(client, client.getProfile());
    }

    private BxConnection openSerial(Screen screen) throws Exception {
        // 串口：ip 形如 "COM2" 或 "COM2:115200"（BX 要求串口号大写）
        String spec = screen.getIp() == null ? "" : screen.getIp().trim();
        int colon = spec.indexOf(':');
        String port = colon > 0 ? spec.substring(0, colon) : spec;
        port = port.toUpperCase(Locale.ROOT);
        Bx6GScreenRS rs = new Bx6GScreenRS(screen.getId(), cardOf(screen));
        boolean connected = colon > 0
                ? rs.connect(
                        port,
                        baudRate(Integer.parseInt(spec.substring(colon + 1).trim())))
                : rs.connect(port);
        if (!connected) {
            throw new LedException("Connect(" + port + ")", -1, "打开串口失败");
        }
        applyTuning(rs, screen);
        return new BxConnection(rs, rs.getProfile());
    }

    private static Bx6GScreenRS.BaudRate baudRate(int baud) {
        return baud == 9600 ? Bx6GScreenRS.BaudRate.RATE_9600 : Bx6GScreenRS.BaudRate.RATE_57600;
    }

    private void applyTuning(Bx6GScreen screen, Screen cfg) {
        if (cfg.getTuning() == null) {
            return;
        }
        Screen.ScreenTuning tuning = cfg.getTuning();
        try {
            if (tuning.isAdjustTime()) {
                requireOk(screen.syncTime(), "SyncTime");
            }
            if (tuning.getPowerOn() != null) {
                requireOk(
                        tuning.getPowerOn() ? screen.turnOn() : screen.turnOff(),
                        tuning.getPowerOn() ? "TurnOn" : "TurnOff");
            }
            if (tuning.getBrightness() != null) {
                int v = Math.max(1, Math.min(16, tuning.getBrightness()));
                requireOk(screen.manualBrightness((byte) v), "ManualBrightness");
            }
        } catch (Exception e) {
            log.warn("[警告] 屏 {} 屏参微调失败: {}", cfg.getId(), e.getMessage());
        }
    }

    /** 卡型号：由 ledType 推断（BX 接口用，非 JNI 卡型枚举）。 */
    private static Bx6Card cardOf(Screen screen) {
        return switch (screen.getLedType()) {
            case 1 -> new Bx6E(); // E 系列
            case 2 -> new Bx6X(); // X 系列
            case 4 -> new Bx6E(); // E5/E6/C8
            case 5 -> new Bx6Q(); // D2/D4/D6（6Q 兼容）
            default -> new Bx6M(); // M 通用
        };
    }

    private static Rect rectOf(Area a, Screen screen) {
        return a.getRect() != null ? a.getRect() : new Rect(0, 0, screen.getWidth(), screen.getHeight());
    }

    private static boolean isNeima(Area a) {
        return a.getType() != null && "neima".equalsIgnoreCase(a.getType().trim());
    }

    private static Program findProgram(Screen screen, int programNo) {
        for (Program p : screen.getPrograms()) {
            if (p.getProgramNo() == programNo) {
                return p;
            }
        }
        return null;
    }

    // ==================== 定时 ====================

    private void applySchedule(ProgramBxFile pf, Program.ProgramSchedule ps) {
        if (ps == null || !ps.isEnable()) {
            return;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(ps.getStart(), DATE_TIME);
            LocalDateTime end = LocalDateTime.parse(ps.getEnd(), DATE_TIME);
            pf.setupStartEndDate(toDate(start), toDate(end));

            ProgramWeek week = pf.getProgramWeek();
            if (week == null) {
                return;
            }
            week.off();
            week.monday = (ps.getWeekValue() & 0x02) != 0;
            week.tuesday = (ps.getWeekValue() & 0x04) != 0;
            week.wednesday = (ps.getWeekValue() & 0x08) != 0;
            week.thursday = (ps.getWeekValue() & 0x10) != 0;
            week.friday = (ps.getWeekValue() & 0x20) != 0;
            week.saturday = (ps.getWeekValue() & 0x40) != 0;
            week.sunday = (ps.getWeekValue() & 0x01) != 0;
        } catch (Exception e) {
            log.warn("[警告] 节目定时设置失败: {}", e.getMessage());
        }
    }

    private static Date toDate(LocalDateTime dt) {
        return new Date(java.sql.Timestamp.valueOf(dt).getTime());
    }

    // ==================== 样式/工具 ====================

    private static onbon.bx06.area.WeekStyle weekStyle(Integer weekFormat) {
        if (weekFormat != null) {
            return switch (weekFormat) {
                case 1 -> onbon.bx06.area.WeekStyle.ENG;
                case 2 -> onbon.bx06.area.WeekStyle.ENGLISH;
                default -> onbon.bx06.area.WeekStyle.CHINESE;
            };
        }
        return onbon.bx06.area.WeekStyle.CHINESE;
    }

    private static onbon.bx06.area.TimeStyle timeStyle(Integer timeFormat) {
        if (timeFormat != null) {
            return switch (timeFormat) {
                case 1 -> onbon.bx06.area.TimeStyle.HH_MM_SS_2;
                case 2 -> onbon.bx06.area.TimeStyle.HH_MM_1;
                case 3 -> onbon.bx06.area.TimeStyle.HH_MM_2;
                case 4 -> onbon.bx06.area.TimeStyle.HH12_MM_SS_1;
                case 5 -> onbon.bx06.area.TimeStyle.HH12_MM_SS_2;
                case 6 -> onbon.bx06.area.TimeStyle.AMPM_HH_MM;
                default -> onbon.bx06.area.TimeStyle.HH_MM_SS_1;
            };
        }
        return onbon.bx06.area.TimeStyle.HH_MM_SS_1;
    }

    private static Font toAwtFont(Area.Font f) {
        int style = Font.PLAIN;
        if (f.isBold()) {
            style |= Font.BOLD;
        }
        if (f.isItalic()) {
            style |= Font.ITALIC;
        }
        String name = f.getName() == null || f.getName().isBlank() ? "宋体" : f.getName();
        int size = f.getSize() > 0 ? f.getSize() : 16;
        return new Font(name, style, size);
    }

    /**
     * 配置颜色（BBGGRR 或颜色名）→ AWT 颜色。
     *
     * <p>{@link Colors#of} 返回 BBGGRR 整数（红=0x0000ff），这里按 BGR 顺序还原 RGB。
     */
    private static Color toAwtColor(String nameOrHex) {
        int bbggrr = Colors.of(nameOrHex);
        int r = bbggrr & 0xff;
        int g = (bbggrr >> 8) & 0xff;
        int b = (bbggrr >> 16) & 0xff;
        return new Color(r, g, b);
    }

    private static boolean nvl(Boolean b) {
        return b != null && b;
    }

    private static void requireOk(Bx6GScreen.Result<?> result, String action) {
        if (result == null || !result.isOK()) {
            throw new LedException(action, -1, "返回失败: " + (result == null ? "no reply" : result));
        }
    }

    private static LedException asLedException(String action, Exception e) {
        if (e instanceof LedException le) {
            return le;
        }
        return new LedException(action, -1, e.getMessage(), e);
    }

    @Override
    public Charset encoding(LedConfig config) {
        String enc = config.getSdk().getEncoding();
        try {
            return Charset.forName(enc == null || enc.isBlank() ? "GBK" : enc);
        } catch (Exception e) {
            return Charset.forName("GBK");
        }
    }

    @Override
    public String displayName() {
        return "仰邦 bx06 (" + Bx6GEnv.VER_INFO + ")";
    }

    /** 到控制卡的连接 + 屏参（profile），try-with-resources 自动断开。 */
    private static final class BxConnection implements AutoCloseable {
        private final Bx6GScreen screen;
        private final Bx6GScreenProfile profile;

        private BxConnection(Bx6GScreen screen, Bx6GScreenProfile profile) {
            this.screen = screen;
            this.profile = profile;
        }

        private Bx6GScreen screen() {
            return screen;
        }

        private Bx6GScreenProfile profile() {
            return profile;
        }

        @Override
        public void close() {
            try {
                screen.disconnect();
            } catch (Exception ignore) {
                // 断开失败不影响主流程
            }
        }
    }

    /** 从动态区模板取字体（刷新页沿用内码区字号）。 */
    private static Font areaFont(DynamicBxArea area) {
        Font f = area.getFont();
        return f != null ? f : new Font("宋体", Font.PLAIN, 16);
    }
}
