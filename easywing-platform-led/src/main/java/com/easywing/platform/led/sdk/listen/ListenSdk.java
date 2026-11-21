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
package com.easywing.platform.led.sdk.listen;

import cn.com.listentech.LEDControl;
import cn.com.listentech.ModuleInfo;
import cn.com.listentech.ScanLineInfo;
import cn.com.listentech.ScreenInfo;
import com.easywing.platform.led.config.Area;
import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Program;
import com.easywing.platform.led.config.Rect;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.engine.ProgramBuilder;
import com.easywing.platform.led.sdk.LedException;
import com.easywing.platform.led.sdk.LedSdk;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 灵信五代单双色 V2014 适配（{@code ListenPlayDll.dll}，64 位）。
 *
 * <p>采用会话句柄模型：
 * <pre>
 *   StartSend() → SetTransMode(...) → SetNetworkPara / SetSerialPortPara
 *     → AddControl / AddProgram / SetProgramTimer / AddXxx 区域
 *     → SendControl(...) → EndSend(...)
 * </pre>
 *
 * <p><b>不支持内码区实时刷新</b>（DLL 未导出 Refresh*），内码区只能随整屏下发。
 *
 * <p><b>DLL 加载要点</b>：依赖同目录 ffmpeg（avutil-54 等 8 个）与 {@code ListenTechMT.dll}，
 * Windows 的 {@code LoadLibrary} 不会搜索 DLL 所在目录，必须按拓扑顺序预加载。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
public final class ListenSdk implements LedSdk {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int TRANS_NETWORK = 1;
    private static final int TRANS_SERIAL = 2;
    private static final int SEND_NORMAL = 1;
    /** IP 可达探测的 TCP 连接超时（毫秒）。 */
    private static final int PING_TIMEOUT_MS = 1500;

    /** ListenPlayDll 依赖的同目录 ffmpeg 运行库，必须按拓扑顺序预加载。 */
    private static final String[] DEPENDENCIES = {
        "avutil-54.dll",
        "swresample-1.dll",
        "swscale-3.dll",
        "postproc-53.dll",
        "avcodec-56.dll",
        "avformat-56.dll",
        "avfilter-5.dll",
        "avdevice-56.dll",
        "ListenTechMT.dll"
    };

    private volatile boolean initialized;
    private volatile LedConfig config;
    private volatile String loadedPath;

    @Override
    public String provider() {
        return "listen";
    }

    @Override
    public synchronized void init(LedConfig cfg) {
        if (initialized) {
            return;
        }
        this.config = cfg;
        String rel = cfg.getSdk().getListenDllPath() == null
                        || cfg.getSdk().getListenDllPath().isBlank()
                ? "sdk/ListenPlayDll.dll"
                : cfg.getSdk().getListenDllPath();
        Path path = resolve(rel);
        if (!Files.exists(path)) {
            throw new LedException("加载DLL", -1, "V2014 DLL 不存在: " + path);
        }
        if (loadedPath != null && loadedPath.equals(path.toString())) {
            initialized = true;
            return;
        }
        log.info("[SDK] 加载 V2014: {}", path);
        try {
            System.setProperty("ledsystem.listen.dll", path.toString());
            preloadDependencies(path.getParent());
            System.load(path.toString());
        } catch (Throwable t) {
            throw new LedException("加载DLL", -1, "加载 V2014 DLL 失败: " + t.getMessage(), t);
        }
        loadedPath = path.toString();
        initialized = true;
        log.info("[SDK] V2014 就绪 ({})", path.getFileName());
    }

    @Override
    public boolean initialized() {
        return initialized;
    }

    private static Path resolve(String rel) {
        Path p = Paths.get(rel);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path cwd = Paths.get("").toAbsolutePath().resolve(p).normalize();
        if (Files.exists(cwd)) {
            return cwd;
        }
        try {
            Path jar = Paths.get(ListenSdk.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toAbsolutePath();
            Path fromJar =
                    jar.getParent() == null ? null : jar.getParent().resolve(p).normalize();
            if (fromJar != null && Files.exists(fromJar)) {
                return fromJar;
            }
        } catch (Exception ignored) {
            // 忽略，用 cwd 路径
        }
        return cwd;
    }

    private static void preloadDependencies(Path dir) {
        if (dir == null) {
            return;
        }
        for (String name : DEPENDENCIES) {
            Path dep = dir.resolve(name);
            if (!Files.exists(dep)) {
                continue;
            }
            try {
                System.load(dep.toString());
            } catch (Throwable t) {
                log.warn("[SDK] 预加载依赖失败 {}: {}", name, t.getMessage());
            }
        }
    }

    // ================= 节目下发 =================

    @Override
    public DeployResult deploy(Screen screen, LedConfig cfg) {
        int ok = 0;
        int failed = 0;
        String lastError = null;

        for (Program program : screen.getPrograms()) {
            try {
                deployOne(screen, cfg, program);
                ok++;
            } catch (Exception e) {
                failed++;
                lastError = e.getMessage();
                log.warn("[部署失败] {} 节目 {}: {}", screen, program.getProgramNo(), e.getMessage());
            }
        }
        return new DeployResult(ok, failed, lastError);
    }

    @Override
    public DeployResult deployProgram(Screen screen, LedConfig cfg, int programNo) {
        Program target = findProgram(screen, programNo);
        try {
            deployOne(screen, cfg, target);
            return new DeployResult(1, 0, null);
        } catch (Exception e) {
            log.warn("[部署失败] {} 节目 {}: {}", screen, programNo, e.getMessage());
            return new DeployResult(0, 1, e.getMessage());
        }
    }

    private void deployOne(Screen screen, LedConfig cfg, Program program) {
        int handle = 0;
        try {
            handle = beginSession(screen, cfg);
            int jno = program.getProgramNo() + 1;
            int pno = 1;

            AddCheck.onOK("AddProgram", LEDControl.AddProgram(handle, jno, program.getPlayTime()));
            if (program.getSchedule() != null && program.getSchedule().isEnable()) {
                applySchedule(handle, pno, jno, program.getSchedule());
            }
            for (Area area : program.getAreas()) {
                addArea(handle, jno, area, screen);
            }
            int code = LEDControl.SendControl(handle, SEND_NORMAL, 0);
            if (code != 1) {
                throw new LedException("SendControl(" + screen.getIp() + ")", code, sendDescribe(code));
            }
        } finally {
            if (handle != 0) {
                try {
                    LEDControl.EndSend(handle);
                } catch (Throwable ignored) {
                    // 结束会话失败不掩盖主错误
                }
            }
        }
    }

    private static Program findProgram(Screen screen, int programNo) {
        for (Program p : screen.getPrograms()) {
            if (p.getProgramNo() == programNo) {
                return p;
            }
        }
        throw new LedException(
                "Program(" + screen.getId() + ", " + programNo + ")",
                -3,
                "屏 " + screen.getId() + " 未配置节目 " + programNo);
    }

    private int beginSession(Screen screen, LedConfig cfg) {
        int handle = LEDControl.StartSend();
        if (handle == 0) {
            throw new LedException("StartSend", 0, "启动通讯会话失败（返回 0）");
        }
        int pno = 1;
        String conn =
                screen.getConnection() == null ? "tcp" : screen.getConnection().toLowerCase();
        int transMode = "serial".equals(conn) ? TRANS_SERIAL : TRANS_NETWORK;
        int controlType = controlType(screen, cfg);
        int code = LEDControl.SetTransMode(handle, transMode, 1, controlType, pno);
        if (code != 1) {
            throw new LedException("SetTransMode", code, "设置通讯模式失败: " + code);
        }
        if (transMode == TRANS_SERIAL) {
            SerialSpec sp = SerialSpec.parse(screen.getIp());
            AddCheck.onNetwork("SetSerialPortPara", LEDControl.SetSerialPortPara(handle, pno, sp.port, sp.baud));
        } else {
            AddCheck.onNetwork(
                    "SetNetworkPara", LEDControl.SetNetworkPara(handle, pno, screen.getIp(), listenPort(cfg)));
        }
        AddCheck.onOK("AddControl", LEDControl.AddControl(handle, pno, dbColor(screen)));
        return handle;
    }

    private static int controlType(Screen screen, LedConfig cfg) {
        return screen.getLedType() > 0 ? screen.getLedType() : cfg.getSdk().getLedType();
    }

    private static int dbColor(Screen screen) {
        return screen.getColorType() > 0 ? screen.getColorType() : 1;
    }

    private static int listenPort(LedConfig cfg) {
        return cfg.getSdk().getListenPort() > 0 ? cfg.getSdk().getListenPort() : 10000;
    }

    private void applySchedule(int handle, int pno, int jno, Program.ProgramSchedule ps) {
        LocalDateTime start = LocalDateTime.parse(ps.getStart(), DATE_TIME);
        LocalDateTime end = LocalDateTime.parse(ps.getEnd(), DATE_TIME);
        int timingModel = timingModel(ps.getEnableFlag());
        int code = LEDControl.SetProgramTimer(
                handle,
                pno,
                jno,
                timingModel,
                ps.getWeekValue(),
                start.getSecond(),
                start.getMinute(),
                start.getHour(),
                start.getDayOfMonth(),
                start.getMonthValue(),
                start.getDayOfWeek().getValue() % 7 + 1,
                start.getYear(),
                end.getSecond(),
                end.getMinute(),
                end.getHour(),
                end.getDayOfMonth(),
                end.getMonthValue(),
                end.getDayOfWeek().getValue() % 7 + 1,
                end.getYear());
        AddCheck.onOK("SetProgramTimer", code);
    }

    private static int timingModel(int enableFlag) {
        boolean date = (enableFlag & 0x01) != 0;
        boolean time = (enableFlag & 0x02) != 0;
        boolean week = (enableFlag & 0x04) != 0;
        if (date && time && week) {
            return 7;
        }
        if (date && time) {
            return 3;
        }
        if (date && week) {
            return 5;
        }
        if (time && week) {
            return 6;
        }
        if (date) {
            return 2;
        }
        if (week) {
            return 4;
        }
        return 1;
    }

    private void addArea(int handle, int jno, Area a, Screen screen) {
        Rect r = a.getRect() != null ? a.getRect() : new Rect(0, 0, screen.getWidth(), screen.getHeight());
        String type = a.getType() == null ? "" : a.getType().toLowerCase();
        String action = "AddArea(" + type + ", areaNo=" + a.getAreaNo() + ")";
        try {
            switch (type) {
                case "neima" -> AddCheck.onOK(action, addNeima(handle, jno, a, r));
                case "multiline" -> AddCheck.onOK(action, addMultiline(handle, jno, a, r));
                case "singleline" -> AddCheck.onOK(action, addSingleline(handle, jno, a, r));
                case "static" -> AddCheck.onOK(action, addStatic(handle, jno, a, r));
                case "clock" -> AddCheck.onOK(action, addClock(handle, jno, a, r));
                case "time" -> AddCheck.onOK(action, addTimer(handle, jno, a, r));
                case "image" -> AddCheck.onOK(action, addImage(handle, jno, a, r));
                case "border" -> AddCheck.onOK(action, addBorder(handle, jno, a, r));
                case "voice", "qrcode" -> throw new LedException(action, -1, "V2014 不支持的区域类型: " + a.getType());
                default -> throw new LedException(action, -1, "不支持的区域类型: " + a.getType());
            }
        } catch (LedException e) {
            throw e;
        } catch (Exception e) {
            throw new LedException(action, -1, e.getMessage(), e);
        }
    }

    private static int addNeima(int handle, int jno, Area a, Rect r) {
        int fontSize = a.getFontSize() != null ? a.getFontSize() : 16;
        int showStyle = fontSize >= 32 ? 32 : 16;
        int fontcolor = ListenColors.simple(
                a.getFontColor() != null ? a.getFontColor() : a.effectiveFont().getColor());
        int playStyle = clamp(a.getInStyle(), 0, 11);
        int speed = clamp(a.getSpeed(), 1, 255);
        int times = a.getDelay() > 0 ? a.getDelay() : 10;
        return LEDControl.AddNeiMaTxtArea(
                handle,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                nvl(a.getContent()),
                showStyle,
                0,
                fontcolor,
                fontSize,
                playStyle,
                255,
                speed,
                times);
    }

    private static int addMultiline(int handle, int jno, Area a, Rect r) {
        int code = LEDControl.AddFileArea(
                handle, jno, a.getAreaNo(), r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), 0);
        if (code != 1) {
            return code;
        }
        int addType = a.getAddType() != null ? a.getAddType() : 0;
        if (addType == 1 && a.getFilePath() != null && !a.getFilePath().isBlank()) {
            return LEDControl.AddFile(
                    handle,
                    jno,
                    a.getAreaNo(),
                    1,
                    a.getFilePath(),
                    r.getWidth(),
                    r.getHeight(),
                    clamp(a.getInStyle(), 0, 38),
                    255,
                    clamp(a.getSpeed(), 1, 255),
                    clamp(a.getDelay(), 0, 65535),
                    1);
        }
        Area.Font f = a.effectiveFont();
        return LEDControl.AddFileString(
                handle,
                jno,
                a.getAreaNo(),
                1,
                nvl(a.getContent()),
                f.getName(),
                f.getSize(),
                ListenColors.rgb(f.getColor()),
                f.isBold(),
                f.isItalic(),
                f.isUnderline(),
                align(a.getAlignment()),
                r.getWidth(),
                r.getHeight(),
                clamp(a.getInStyle(), 0, 38),
                255,
                clamp(a.getSpeed(), 1, 255),
                clamp(a.getDelay(), 0, 65535),
                vcenter(a));
    }

    private static int addSingleline(int handle, int jno, Area a, Rect r) {
        int addType = a.getAddType() != null ? a.getAddType() : 0;
        if (addType == 1 && a.getFilePath() != null && !a.getFilePath().isBlank()) {
            return LEDControl.AddLnTxtArea(
                    handle,
                    jno,
                    a.getAreaNo(),
                    r.getLeft(),
                    r.getTop(),
                    r.getWidth(),
                    r.getHeight(),
                    a.getFilePath(),
                    clamp(a.getInStyle(), 0, 38),
                    clamp(a.getSpeed(), 1, 255),
                    1);
        }
        Area.Font f = a.effectiveFont();
        return LEDControl.AddLnTxtString(
                handle,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                nvl(a.getContent()),
                f.getName(),
                f.getSize(),
                ListenColors.rgb(f.getColor()),
                f.isBold(),
                f.isItalic(),
                f.isUnderline(),
                clamp(a.getInStyle(), 0, 38),
                clamp(a.getSpeed(), 1, 255),
                1);
    }

    private static int addStatic(int handle, int jno, Area a, Rect r) {
        int addType = a.getAddType() != null ? a.getAddType() : 0;
        Area.Font f = a.effectiveFont();
        if (addType == 1 && a.getFilePath() != null && !a.getFilePath().isBlank()) {
            return LEDControl.AddQuitText(
                    handle,
                    jno,
                    a.getAreaNo(),
                    r.getLeft(),
                    r.getTop(),
                    r.getWidth(),
                    r.getHeight(),
                    ListenColors.rgb(f.getColor()),
                    f.getName(),
                    f.getSize(),
                    f.isBold(),
                    f.isItalic(),
                    f.isUnderline(),
                    a.getFilePath());
        }
        return LEDControl.AddQuitText(
                handle,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                ListenColors.rgb(f.getColor()),
                f.getName(),
                f.getSize(),
                f.isBold(),
                f.isItalic(),
                f.isUnderline(),
                nvl(a.getContent()));
    }

    private static int addClock(int handle, int jno, Area a, Rect r) {
        Area.Font f = a.effectiveFont();
        int[] show = clockFlags(a.getShow());
        int color = ListenColors.simple(a.getDateColor() != null ? a.getDateColor() : f.getColor());
        int twoOrFourYear = a.getDateFormat() != null && a.getDateFormat() == 2 ? 2 : 1;
        int hourShow = 1;
        int format = a.getDateFormat() != null ? clamp(a.getDateFormat(), 0, 3) : 0;
        return LEDControl.AddDClockArea(
                handle,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                color,
                f.getName(),
                f.getSize(),
                f.isBold() ? 1 : 0,
                f.isItalic() ? 1 : 0,
                f.isUnderline() ? 1 : 0,
                show[0],
                show[1],
                show[2],
                show[3],
                show[4],
                show[5],
                show[6],
                twoOrFourYear,
                hourShow,
                format,
                0,
                0,
                0);
    }

    private static int addTimer(int handle, int jno, Area a, Rect r) {
        Area.Font f = a.effectiveFont();
        LocalDateTime end =
                a.getEndTime() != null ? LocalDateTime.parse(a.getEndTime(), DATE_TIME) : LocalDateTime.now();
        int mode = a.getCountMode() != null && a.getCountMode() == 1 ? 1 : 2;
        int dayshow = a.getShowDay() == null || a.getShowDay() ? 2 : 1;
        return LEDControl.AddTimerArea(
                handle,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                ListenColors.simple(f.getColor()),
                f.getName(),
                f.getSize(),
                f.isBold() ? 1 : 0,
                f.isItalic() ? 1 : 0,
                f.isUnderline() ? 1 : 0,
                mode,
                dayshow,
                end.getDayOfWeek().getValue() % 7 + 1,
                end.getDayOfMonth(),
                end.getHour(),
                end.getMinute(),
                end.getSecond(),
                end.getYear(),
                end.getDayOfWeek().getValue() % 7 + 1,
                end.getMonthValue(),
                end.getDayOfMonth(),
                end.getHour(),
                end.getMinute(),
                end.getSecond());
    }

    private static int addImage(int handle, int jno, Area a, Rect r) {
        int code = LEDControl.AddFileArea(
                handle, jno, a.getAreaNo(), r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), 0);
        if (code != 1) {
            return code;
        }
        String file = a.getFilePath() != null && !a.getFilePath().isBlank() ? a.getFilePath() : nvl(a.getContent());
        return LEDControl.AddFile(
                handle,
                jno,
                a.getAreaNo(),
                1,
                file,
                r.getWidth(),
                r.getHeight(),
                clamp(a.getInStyle(), 0, 38),
                255,
                clamp(a.getSpeed(), 1, 255),
                clamp(a.getDelay(), 0, 65535),
                1);
    }

    private static int addBorder(int handle, int jno, Area a, Rect r) {
        int color = a.getBorderColor() != null ? ListenColors.rgb(hexToString(a.getBorderColor())) : 255;
        int style = a.getBorderStyle() != null ? a.getBorderStyle() : 0;
        int speed = a.getBorderSpeed() != null ? clamp(a.getBorderSpeed(), 0, 31) : 10;
        return LEDControl.AddWaterFrame(
                handle,
                1,
                jno,
                a.getAreaNo(),
                r.getLeft(),
                r.getTop(),
                r.getWidth(),
                r.getHeight(),
                1,
                1,
                style,
                0,
                1,
                speed,
                color,
                0);
    }

    // ================= 控制能力 =================

    /**
     * 切换屏当前播放节目。
     *
     * <p><b>V2014 未导出 {@code ProgramSwitch}</b>，退化为重发指定节目，语义有差异（见能力矩阵文档）。
     */
    @Override
    public void switchProgram(Screen screen, int programIndex) {
        Program target = findProgram(screen, programIndex);
        log.info("[切换] {} → 重发节目 {}（V2014 无 ProgramSwitch 导出，实为重发）", screen.getId(), programIndex);
        try {
            deployOne(screen, config, target);
        } catch (LedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LedException(
                    "ProgramSwitch(" + screen.getIp() + ", " + programIndex + ")",
                    -1,
                    "V2014 无切节目导出，重发节目失败: " + e.getMessage(),
                    e);
        }
    }

    /**
     * 检测屏是否在线。V2014 未导出 {@code TestOnline}，退化为 IP 可达探测。
     */
    @Override
    public boolean isOnline(Screen screen) {
        String ip = screen.getIp();
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String conn =
                screen.getConnection() == null ? "tcp" : screen.getConnection().toLowerCase();
        if ("serial".equals(conn)) {
            return false;
        }
        int port = listenPort(config);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), PING_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void setScreenInfo(Screen screen, LedConfig cfg, int width, int height, int colorType) {
        int handle = controlSession(screen, cfg);
        try {
            ScreenInfo si = new ScreenInfo();
            si.screenwidth = width;
            si.screenheight = height;
            si.dbcolor = colorType;
            si.baud = screen.getTuning() != null && screen.getTuning().getBaud() != null
                    ? screen.getTuning().getBaud()
                    : 115200;
            si.screenname = screen.getTuning() != null && screen.getTuning().getScreenName() != null
                    ? screen.getTuning().getScreenName()
                    : nvl(screen.getName());
            int code = LEDControl.SetScreenInfo(handle, si);
            AddCheck.onOK("SetScreenInfo", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void adjustTime(Screen screen, LedConfig cfg) {
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.AdjustTime(handle, 0);
            AddCheck.onControl("AdjustTime", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void setPower(Screen screen, LedConfig cfg, boolean on) {
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.SetPower(handle, on ? 0 : 1);
            AddCheck.onControl("SetPower", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void setBrightness(Screen screen, LedConfig cfg, int value) {
        int v = clamp(value, 1, 16);
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.SetBrightness(handle, v);
            AddCheck.onControl("SetBrightness", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void setBrightnessTimer(Screen screen, LedConfig cfg, List<int[]> groups, List<Integer> brightness) {
        int[][] g = padGroups(groups, 4);
        int[] b = padBrightness(brightness);
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.SetBrightnessTimer(
                    handle, 0, g[0][0], g[0][1], g[0][2], g[0][3], b[0], g[1][0], g[1][1], g[1][2], g[1][3], b[1],
                    g[2][0], g[2][1], g[2][2], g[2][3], b[2]);
            AddCheck.onControl("SetBrightnessTimer", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void setPowerTimer(Screen screen, LedConfig cfg, List<int[]> groups) {
        int[][] g = padGroups(groups, 4);
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.SetPowerTimer(
                    handle, 0, g[0][0], g[0][1], g[0][2], g[0][3], g[1][0], g[1][1], g[1][2], g[1][3], g[2][0], g[2][1],
                    g[2][2], g[2][3], 0);
            AddCheck.onControl("SetPowerTimer", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void searchController(Screen screen, LedConfig cfg, String iniFilePath, boolean broadcast) {
        String ini = iniFilePath;
        if (ini == null || ini.isBlank()) {
            ini = screen.getSearchIniPath();
        }
        if (ini == null || ini.isBlank()) {
            ini = "listen-search-" + safeFileName(screen.getId()) + ".ini";
        }
        Path path = Paths.get(ini);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        path = path.normalize();
        if (!path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ini")) {
            throw new LedException("SearchController", -1, "搜索结果文件必须是 .ini: " + path);
        }
        int handle = controlSession(screen, cfg);
        try {
            int code = LEDControl.SearchController(handle, path.toString(), broadcast, 0);
            AddCheck.onControl("SearchController", code);
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public void setScanLineInfo(Screen screen, LedConfig cfg) {
        if (screen.getScan() == null) {
            throw new LedException("SetScanLineInfo", -1, "屏 " + screen.getId() + " 未配置 scan 参数");
        }
        ScanTuningHolder st = toHolder(screen.getScan());
        int handle = controlSession(screen, cfg);
        try {
            ScanLineInfo sli = new ScanLineInfo();
            sli.emodulewidth = st.moduleWidth;
            sli.ecloumndot = st.columnDot;
            sli.escanstyle = st.scanStyle;
            sli.edtataheight = st.dataHeight;
            AddCheck.onOK("SetScanLineInfo", LEDControl.SetScanLineInfo(handle, sli));
            if (st.hasModule) {
                ModuleInfo mi = new ModuleInfo();
                mi.edbcolor = st.dbColor;
                mi.edecode = st.decode;
                mi.eoe = st.oe;
                mi.eda = st.da;
                mi.erg = st.rgSwap;
                AddCheck.onOK("SetModuleInfo", LEDControl.SetModuleInfo(handle, mi));
            }
        } finally {
            endQuietly(handle);
        }
    }

    @Override
    public boolean supportsNeimaRefresh() {
        return false;
    }

    @Override
    public Charset encoding(LedConfig cfg) {
        if (cfg == null
                || cfg.getSdk().getEncoding() == null
                || cfg.getSdk().getEncoding().isBlank()) {
            return Charset.defaultCharset();
        }
        try {
            return Charset.forName(cfg.getSdk().getEncoding().trim());
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    @Override
    public String displayName() {
        return "灵信五代 V2014 (ListenPlayDll.dll)";
    }

    // ================= 会话辅助 =================

    private int controlSession(Screen screen, LedConfig cfg) {
        int handle = LEDControl.StartSend();
        if (handle == 0) {
            throw new LedException("StartSend", 0, "启动通讯会话失败（返回 0）");
        }
        int pno = 1;
        String conn =
                screen.getConnection() == null ? "tcp" : screen.getConnection().toLowerCase();
        int transMode = "serial".equals(conn) ? TRANS_SERIAL : TRANS_NETWORK;
        int code = LEDControl.SetTransMode(handle, transMode, 1, controlType(screen, cfg), pno);
        if (code != 1) {
            endQuietly(handle);
            throw new LedException("SetTransMode", code, "设置通讯模式失败: " + code);
        }
        if (transMode == TRANS_SERIAL) {
            SerialSpec sp = SerialSpec.parse(screen.getIp());
            AddCheck.onNetwork("SetSerialPortPara", LEDControl.SetSerialPortPara(handle, pno, sp.port, sp.baud));
        } else {
            AddCheck.onNetwork(
                    "SetNetworkPara", LEDControl.SetNetworkPara(handle, pno, screen.getIp(), listenPort(cfg)));
        }
        return handle;
    }

    private static void endQuietly(int handle) {
        try {
            LEDControl.EndSend(handle);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    // ================= 工具 =================

    private static int align(Integer alignment) {
        if (alignment == null) {
            return 1;
        }
        return switch (alignment) {
            case 1 -> 3;
            case 2 -> 2;
            default -> 1;
        };
    }

    private static int vcenter(Area a) {
        return a.getVcenter() != null && a.getVcenter() ? 2 : 1;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static int[] clockFlags(List<String> show) {
        int[] flags = new int[7];
        if (show == null || show.isEmpty()) {
            flags[4] = flags[5] = flags[6] = 1;
            return flags;
        }
        for (String s : show) {
            switch (s.toLowerCase()) {
                case "year":
                    flags[0] = 1;
                    break;
                case "week":
                    flags[1] = 1;
                    break;
                case "month":
                    flags[2] = 1;
                    break;
                case "day":
                    flags[3] = 1;
                    break;
                case "hour":
                    flags[4] = 1;
                    break;
                case "minute":
                    flags[5] = 1;
                    break;
                case "second":
                    flags[6] = 1;
                    break;
                default:
                    break;
            }
        }
        return flags;
    }

    private static int[][] padGroups(List<int[]> groups, int width) {
        int[][] g = new int[3][width];
        for (int i = 0; i < 3; i++) {
            g[i] = new int[width];
            if (groups != null && i < groups.size() && groups.get(i) != null) {
                int[] src = groups.get(i);
                for (int j = 0; j < width && j < src.length; j++) {
                    g[i][j] = src[j];
                }
            }
        }
        return g;
    }

    private static int[] padBrightness(List<Integer> brightness) {
        int[] b = new int[3];
        for (int i = 0; i < 3; i++) {
            if (brightness != null && i < brightness.size() && brightness.get(i) != null) {
                b[i] = clamp(brightness.get(i), 1, 16);
            } else {
                b[i] = 8;
            }
        }
        return b;
    }

    private static String hexToString(int color) {
        return String.format("%06x", color & 0xffffff);
    }

    private static String safeFileName(String id) {
        return id == null ? "screen" : id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String sendDescribe(int code) {
        return switch (code) {
            case 0 -> "没有节目或端口被占用";
            case 2 -> "通讯失败";
            case 3 -> "发送过程中出错";
            default -> "未知发送结果";
        };
    }

    /** 下发/控制类返回码校验。 */
    private static final class AddCheck {
        private AddCheck() {}

        static void onOK(String action, int code) {
            if (code != 1) {
                throw new LedException(action, code, "返回 " + code + "（1 成功 / 2 参数错误）");
            }
        }

        static void onNetwork(String action, int code) {
            if (code != 1) {
                throw new LedException(action, code, "返回 " + code + "（1 成功 / 2 不成功）");
            }
        }

        static void onControl(String action, int code) {
            if (code != 1) {
                throw new LedException(action, code, "返回 " + code + "（1 成功 / 2 通讯失败 / 3 发送失败）");
            }
        }
    }

    /** 串口参数：{@code "3:115200:1"} 或 {@code "/dev/ttyUSB0:115200:1"}。 */
    private static final class SerialSpec {
        int port = 1;
        int baud = 57600;

        static SerialSpec parse(String spec) {
            SerialSpec s = new SerialSpec();
            if (spec == null || spec.isBlank()) {
                return s;
            }
            String[] parts = spec.split(":");
            if (parts.length >= 1) {
                String p = parts[0].trim();
                try {
                    s.port = Integer.parseInt(p);
                } catch (NumberFormatException ignore) {
                    StringBuilder digits = new StringBuilder();
                    for (int i = p.length() - 1; i >= 0 && Character.isDigit(p.charAt(i)); i--) {
                        digits.insert(0, p.charAt(i));
                    }
                    if (digits.length() > 0) {
                        s.port = Integer.parseInt(digits.toString());
                    }
                }
            }
            if (parts.length >= 2) {
                try {
                    s.baud = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignore) {
                    // 保持默认
                }
            }
            return s;
        }
    }

    private record ScanTuningHolder(
            int moduleWidth,
            int columnDot,
            int scanStyle,
            int dataHeight,
            boolean hasModule,
            int dbColor,
            int decode,
            int oe,
            int da,
            int rgSwap) {}

    private static ScanTuningHolder toHolder(Screen.ScanTuning st) {
        return new ScanTuningHolder(
                st.getModuleWidth() != null ? st.getModuleWidth() : -1,
                st.getColumnDot() != null ? st.getColumnDot() : 0,
                st.getScanStyle() != null ? st.getScanStyle() : 0,
                st.getDataHeight() != null ? st.getDataHeight() : -1,
                st.getDbColor() != null || st.getDecode() != null || st.getOe() != null || st.getDa() != null,
                st.getDbColor() != null ? st.getDbColor() : 1,
                st.getDecode() != null ? st.getDecode() : 0,
                st.getOe() != null ? st.getOe() : 1,
                st.getDa() != null ? st.getDa() : 1,
                st.getRgSwap() != null ? st.getRgSwap() : 0);
    }

    /** 下发结果（与 1.0.8 保持同构）。 */
    public static final class DeployResult extends ProgramBuilder.DeployResult {
        public DeployResult(int ok, int failed, String lastError) {
            super(ok, failed, lastError);
        }
    }
}
