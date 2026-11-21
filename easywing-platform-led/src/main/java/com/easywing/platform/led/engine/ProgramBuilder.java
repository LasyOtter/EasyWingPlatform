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

import com.easywing.platform.led.config.Area;
import com.easywing.platform.led.config.LedConfig;
import com.easywing.platform.led.config.Program;
import com.easywing.platform.led.config.Rect;
import com.easywing.platform.led.config.RefreshConfig;
import com.easywing.platform.led.config.Screen;
import com.easywing.platform.led.sdk.LedException;
import com.easywing.platform.led.sdk.SdkGateway;
import com.listenvision.led;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 灵信 1.0.8 节目构建器：把配置模型翻译成 SDK 的
 * CreateProgram → AddProgram → (SetProgramTime) → AddXxxArea → NetWorkSend → DeleteProgram。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class ProgramBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 构建并下发一个屏的全部节目；返回成功/失败节目数。 */
    public DeployResult deploy(Screen screen, LedConfig config) {
        int ok = 0;
        int failed = 0;
        String lastError = null;

        for (Program program : screen.getPrograms()) {
            boolean done = deployOne(screen, config, program);
            if (done) {
                ok++;
            } else {
                failed++;
                lastError = "下发失败: 节目 " + program.getProgramNo();
            }
        }
        return new DeployResult(ok, failed, lastError);
    }

    /** 按连接方式选择发送接口：tcp/udp 用 NetWorkSend，串口用 SerialPortSend。 */
    private static int send(Screen screen, long hProgram) {
        String conn =
                screen.getConnection() == null ? "tcp" : screen.getConnection().toLowerCase();
        if ("serial".equals(conn)) {
            SerialPortSpec sp = SerialPortSpec.parse(screen.getIp());
            return led.SerialPortSend(sp.port, sp.baud, sp.ledNumber, (int) hProgram);
        }
        return led.NetWorkSend(screen.getIp(), hProgram);
    }

    private static String sendAction(Screen screen) {
        return "serial".equalsIgnoreCase(screen.getConnection()) ? "SerialPortSend" : "NetWorkSend";
    }

    /** 串口通讯参数："3:115200:1" → 串口号 3、波特率 115200、屏号 1。 */
    static final class SerialPortSpec {
        int port = 1;
        int baud = 57600;
        int ledNumber = 1;

        static SerialPortSpec parse(String spec) {
            SerialPortSpec s = new SerialPortSpec();
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
            if (parts.length >= 3) {
                try {
                    s.ledNumber = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException ignore) {
                    // 保持默认
                }
            }
            return s;
        }
    }

    private void applySchedule(long hProgram, int programNo, Program.ProgramSchedule ps) {
        LocalDateTime start = LocalDateTime.parse(ps.getStart(), DATE_TIME);
        LocalDateTime end = LocalDateTime.parse(ps.getEnd(), DATE_TIME);
        SdkGateway.call(
                "SetProgramTime",
                () -> led.SetProgramTime(
                        (int) hProgram,
                        programNo,
                        ps.getEnableFlag(),
                        ps.getWeekValue(),
                        start.getYear(),
                        start.getMonthValue(),
                        start.getDayOfMonth(),
                        start.getHour(),
                        start.getMinute(),
                        start.getSecond(),
                        end.getYear(),
                        end.getMonthValue(),
                        end.getDayOfMonth(),
                        end.getHour(),
                        end.getMinute(),
                        end.getSecond()));
    }

    private void addArea(long hProgram, int programNo, Area a, Screen screen) {
        SdkGateway.call(
                "AddArea(" + a.getType() + ", areaNo=" + a.getAreaNo() + ")",
                () -> dispatchArea(hProgram, programNo, a, screen));
    }

    /** 创建图文区（AddImageTextArea）。文本类接口只能往「已存在的图文区」加内容。 */
    private static int createImageTextArea(long hProgram, int programNo, Area a, Rect r) {
        int layout = a.getLayout() != null ? a.getLayout() : 1;
        return led.AddImageTextArea(
                hProgram, programNo, a.getAreaNo(), r.getLeft(), r.getTop(), r.getWidth(), r.getHeight(), layout);
    }

    private int dispatchArea(long hProgram, int programNo, Area a, Screen screen) {
        Rect r = a.getRect() != null ? a.getRect() : new Rect(0, 0, screen.getWidth(), screen.getHeight());
        String type = a.getType().toLowerCase();
        switch (type) {
            case "neima" -> {
                int fontSize = a.getFontSize() != null ? a.getFontSize() : 16;
                int color = Colors.of(
                        a.getFontColor() != null
                                ? a.getFontColor()
                                : a.effectiveFont().getColor());
                return led.AddNeiMaArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        r.getLeft(),
                        r.getTop(),
                        r.getWidth(),
                        r.getHeight(),
                        nvl(a.getContent()),
                        fontSize,
                        color,
                        a.getInStyle(),
                        a.getSpeed(),
                        a.getDelay());
            }
            case "multiline" -> {
                Area.Font f = a.effectiveFont();
                int addType = a.getAddType() != null ? a.getAddType() : 0;
                String text =
                        a.getAddType() != null && a.getAddType() == 1 ? nvl(a.getFilePath()) : nvl(a.getContent());
                int code = createImageTextArea(hProgram, programNo, a, r);
                if (code != 0) {
                    return code;
                }
                if (a.useBackVariant()) {
                    return led.AddMultiLineTextToImageTextAreaBack(
                            hProgram,
                            programNo,
                            a.getAreaNo(),
                            addType,
                            text,
                            f.getName(),
                            f.getSize(),
                            Colors.of(f.getColor()),
                            f.isBold() ? 1 : 0,
                            f.isItalic() ? 1 : 0,
                            f.isUnderline() ? 1 : 0,
                            a.getInStyle(),
                            a.getSpeed(),
                            a.getDelay(),
                            a.getAlignment() != null ? a.getAlignment() : 0,
                            a.getVcenter() != null && a.getVcenter() ? 1 : 0,
                            a.getBackColor() != null ? a.getBackColor() : 0,
                            a.getRotation() != null ? a.getRotation() : 0);
                }
                return led.AddMultiLineTextToImageTextArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        addType,
                        text,
                        f.getName(),
                        f.getSize(),
                        Colors.of(f.getColor()),
                        f.isBold() ? 1 : 0,
                        f.isItalic() ? 1 : 0,
                        f.isUnderline() ? 1 : 0,
                        a.getInStyle(),
                        a.getSpeed(),
                        a.getDelay(),
                        a.getAlignment() != null ? a.getAlignment() : 0,
                        a.getVcenter() != null && a.getVcenter() ? 1 : 0);
            }
            case "singleline" -> {
                Area.Font f = a.effectiveFont();
                int addType = a.getAddType() != null ? a.getAddType() : 0;
                String text =
                        a.getAddType() != null && a.getAddType() == 1 ? nvl(a.getFilePath()) : nvl(a.getContent());
                int code = createImageTextArea(hProgram, programNo, a, r);
                if (code != 0) {
                    return code;
                }
                if (a.useBackVariant()) {
                    return led.AddSinglelineTextToImageTextAreaBack(
                            hProgram,
                            programNo,
                            a.getAreaNo(),
                            addType,
                            text,
                            f.getName(),
                            f.getSize(),
                            Colors.of(f.getColor()),
                            f.isBold() ? 1 : 0,
                            f.isItalic() ? 1 : 0,
                            f.isUnderline() ? 1 : 0,
                            a.getInStyle(),
                            a.getSpeed(),
                            a.getDelay(),
                            a.getBackColor() != null ? a.getBackColor() : 0,
                            a.getRotation() != null ? a.getRotation() : 0);
                }
                return led.AddSinglelineTextToImageTextArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        addType,
                        text,
                        f.getName(),
                        f.getSize(),
                        Colors.of(f.getColor()),
                        f.isBold() ? 1 : 0,
                        f.isItalic() ? 1 : 0,
                        f.isUnderline() ? 1 : 0,
                        a.getInStyle(),
                        a.getSpeed(),
                        a.getDelay());
            }
            case "static" -> {
                Area.Font f = a.effectiveFont();
                int addType = a.getAddType() != null ? a.getAddType() : 0;
                String text =
                        a.getAddType() != null && a.getAddType() == 1 ? nvl(a.getFilePath()) : nvl(a.getContent());
                int code = createImageTextArea(hProgram, programNo, a, r);
                if (code != 0) {
                    return code;
                }
                if (a.useBackVariant()) {
                    return led.AddStaticTextToImageTextAreaBack(
                            hProgram,
                            programNo,
                            a.getAreaNo(),
                            addType,
                            text,
                            f.getName(),
                            f.getSize(),
                            Colors.of(f.getColor()),
                            f.isBold() ? 1 : 0,
                            f.isItalic() ? 1 : 0,
                            f.isUnderline() ? 1 : 0,
                            a.getDelay(),
                            a.getAlignment() != null ? a.getAlignment() : 0,
                            a.getVcenter() != null && a.getVcenter() ? 1 : 0,
                            a.getBackColor() != null ? a.getBackColor() : 0,
                            a.getRotation() != null ? a.getRotation() : 0);
                }
                return led.AddStaticTextToImageTextArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        addType,
                        text,
                        f.getName(),
                        f.getSize(),
                        Colors.of(f.getColor()),
                        f.isBold() ? 1 : 0,
                        f.isItalic() ? 1 : 0,
                        f.isUnderline() ? 1 : 0,
                        a.getDelay(),
                        a.getAlignment() != null ? a.getAlignment() : 0,
                        a.getVcenter() != null && a.getVcenter() ? 1 : 0);
            }
            case "voice" -> {
                return led.AddVoiceArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        nvl(a.getContent()),
                        a.getDelay(),
                        a.getPlayCount() != null ? a.getPlayCount() : 1);
            }
            case "image" -> {
                int code = createImageTextArea(hProgram, programNo, a, r);
                if (code == 0 && a.getFilePath() != null) {
                    code = led.AddFileToImageTextArea(
                            hProgram,
                            programNo,
                            a.getAreaNo(),
                            a.getFilePath(),
                            a.getInStyle(),
                            a.getSpeed(),
                            a.getDelay());
                }
                return code;
            }
            case "clock" -> {
                Area.Font f = a.effectiveFont();
                int[] flags = clockFlags(a.getShow());
                int dateColor = Colors.of(a.getDateColor() != null ? a.getDateColor() : f.getColor());
                int weekColor = Colors.of(a.getWeekColor() != null ? a.getWeekColor() : f.getColor());
                int timeColor = Colors.of(a.getTimeColor() != null ? a.getTimeColor() : f.getColor());
                return led.AddDigitalClockArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        r.getLeft(),
                        r.getTop(),
                        r.getWidth(),
                        r.getHeight(),
                        f.getName(),
                        f.getSize(),
                        Colors.of(f.getColor()),
                        f.isBold() ? 1 : 0,
                        f.isItalic() ? 1 : 0,
                        f.isUnderline() ? 1 : 0,
                        flags[0],
                        flags[1],
                        flags[2],
                        flags[3],
                        flags[4],
                        flags[5],
                        flags[6],
                        a.getDateFormat() != null ? a.getDateFormat() : 0,
                        dateColor,
                        a.getWeekFormat() != null ? a.getWeekFormat() : 0,
                        weekColor,
                        a.getTimeFormat() != null ? a.getTimeFormat() : 2,
                        timeColor,
                        a.getMultiLine() != null ? a.getMultiLine() : 0);
            }
            case "time" -> {
                Area.Font f = a.effectiveFont();
                LocalDateTime end = LocalDateTime.parse(a.getEndTime(), DATE_TIME);
                return led.AddTimeArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        r.getLeft(),
                        r.getTop(),
                        r.getWidth(),
                        r.getHeight(),
                        f.getName(),
                        f.getSize(),
                        f.isBold() ? 1 : 0,
                        f.isItalic() ? 1 : 0,
                        f.isUnderline() ? 1 : 0,
                        Colors.of(f.getColor()),
                        Colors.of(f.getColor()),
                        a.getShowStyle() != null ? a.getShowStyle() : 0,
                        a.getMultiLine() != null ? a.getMultiLine() : 0,
                        nvl(a.getTxtStr()),
                        end.getYear(),
                        end.getMonthValue(),
                        end.getDayOfMonth(),
                        end.getHour(),
                        end.getMinute(),
                        end.getSecond(),
                        a.getShowDay() == null || a.getShowDay() ? 1 : 0,
                        a.getShowHour() == null || a.getShowHour() ? 1 : 0,
                        a.getShowMinute() == null || a.getShowMinute() ? 1 : 0,
                        a.getShowSecond() == null || a.getShowSecond() ? 1 : 0);
            }
            case "qrcode" -> {
                return led.AddQRCodeArea(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        r.getLeft(),
                        r.getTop(),
                        r.getWidth(),
                        r.getHeight(),
                        a.getLowVersion() != null ? a.getLowVersion() : 1,
                        a.getHighVersion() != null ? a.getHighVersion() : 40,
                        a.getErrLevel() != null ? a.getErrLevel() : 0,
                        a.getMaskVersion() != null ? a.getMaskVersion() : 0xff,
                        a.getBlankPointNum() != null ? a.getBlankPointNum() : 4,
                        a.getBaseCodeColor() != null ? a.getBaseCodeColor() : 6,
                        a.getColorReverse() != null ? a.getColorReverse() : 0,
                        a.getDefaultShow() != null ? a.getDefaultShow() : 1,
                        nvl(a.getDefaultCode() != null ? a.getDefaultCode() : a.getContent()));
            }
            case "border" -> {
                return led.AddBorder(
                        hProgram,
                        programNo,
                        a.getAreaNo(),
                        r.getLeft(),
                        r.getTop(),
                        r.getWidth(),
                        r.getHeight(),
                        a.getBorderType() != null ? a.getBorderType() : 0,
                        a.getBorderValue() != null ? a.getBorderValue() : 0,
                        a.getBorderColor() != null ? a.getBorderColor() : led.COLOR_RED,
                        a.getBorderStyle() != null ? a.getBorderStyle() : 0,
                        a.getBorderSpeed() != null ? a.getBorderSpeed() : 10);
            }
            default -> throw new LedException("AddArea", 0, "不支持的区域类型: " + a.getType());
        }
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

    /** 按配置调度下发指定节目。 */
    public DeployResult deployProgram(Screen screen, LedConfig config, int programNo) {
        Program target = null;
        for (Program p : screen.getPrograms()) {
            if (p.getProgramNo() == programNo) {
                target = p;
                break;
            }
        }
        if (target == null) {
            throw new LedException("deployProgram", -3, "节目不存在: " + programNo);
        }
        boolean ok = deployOne(screen, config, target);
        return new DeployResult(ok ? 1 : 0, ok ? 0 : 1, ok ? null : ("下发失败: 节目 " + programNo));
    }

    private boolean deployOne(Screen screen, LedConfig config, Program program) {
        long hProgram = 0;
        try {
            hProgram = led.CreateProgram(
                    screen.getWidth(),
                    screen.getHeight(),
                    screen.getColorType(),
                    screen.getGrayLevel(),
                    screen.effectiveSaveType(config));
            if (hProgram == 0) {
                throw new LedException("CreateProgram", 0, "返回空句柄（0 表示创建失败）");
            }
            final long handle = hProgram;
            SdkGateway.call(
                    "AddProgram",
                    () -> led.AddProgram(
                            handle, program.getProgramNo(), program.getPlayTime(), program.getLoopCount()));

            if (program.getSchedule() != null && program.getSchedule().isEnable()) {
                applySchedule(hProgram, program.getProgramNo(), program.getSchedule());
            }

            for (Area area : program.getAreas()) {
                addArea(hProgram, program.getProgramNo(), area, screen);
            }

            SdkGateway.call(sendAction(screen) + "(" + screen.getIp() + ")", () -> send(screen, handle));
            return true;
        } catch (Exception e) {
            log.warn("[部署失败] {} 节目 {}: {}", screen, program.getProgramNo(), e.getMessage());
            return false;
        } finally {
            if (hProgram != 0) {
                led.DeleteProgram(hProgram);
            }
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 生成内码区刷新协议字符串（6.3 节）：%disp + 编号 + 颜色 + 特效 + 内容，多区域拼接。 */
    public static String neimaFrame(List<RefreshConfig.Binding> bindings, RefreshContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (RefreshConfig.Binding b : bindings) {
            String text;
            try {
                text = ctx.renderText(b);
            } catch (RuntimeException e) {
                log.warn("[刷新] 区域 {} 取值失败，已跳过: {}", b.getAreaIndex(), e.getMessage());
                continue;
            }
            sb.append("%disp")
                    .append(neimaAreaChar(b.getAreaIndex()))
                    .append(Colors.neimaColorSymbol(b.getColor()))
                    .append(effectDigit(b.getEffect()))
                    .append(text);
        }
        return sb.toString();
    }

    /** 内码区编号按 ASCII 递增：0-9, :, ;, ... O（第 32 个）。 */
    public static char neimaAreaChar(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("内码区顺序号不能为负: " + index);
        }
        if (index > 31) {
            throw new IllegalArgumentException("内码区顺序号最大 31（第 32 个）: " + index);
        }
        return (char) ('0' + index);
    }

    /** 刷新协议只支持 0-9 十种特效（6.3 节），超出归一到 0-9。 */
    public static char effectDigit(int effect) {
        if (effect < 0 || effect > 9) {
            throw new IllegalArgumentException("内码刷新特效只支持 0-9: " + effect);
        }
        return (char) ('0' + effect);
    }

    /** 刷新上下文：把数据源的值套入模板。 */
    public interface RefreshContext {
        String renderText(RefreshConfig.Binding binding);
    }

    /**
     * 下发结果。
     */
    public static class DeployResult {
        public final int ok;
        public final int failed;
        public final String lastError;

        public DeployResult(int ok, int failed, String lastError) {
            this.ok = ok;
            this.failed = failed;
            this.lastError = lastError;
        }

        public boolean success() {
            return failed == 0;
        }

        @Override
        public String toString() {
            return "下发结果: 成功 " + ok + " 个节目, 失败 " + failed + " 个" + (lastError != null ? ", 最后错误: " + lastError : "");
        }
    }
}
