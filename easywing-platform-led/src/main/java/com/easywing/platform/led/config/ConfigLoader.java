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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 读取 YAML 配置并校验，产出 {@link LedConfig} 与 {@link Screen} 列表。
 *
 * <p>三层配置：
 * <pre>
 *   config/led.yml            根配置（SDK / 系统）
 *   config/screens/&lt;id&gt;.yml  屏定义（含 programs）
 *   config/refresh/&lt;id&gt;.yml  内码实时刷新绑定（可选）
 * </pre>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Component
public class ConfigLoader {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path baseDir;

    public ConfigLoader(LedConfigProperties properties) {
        this.baseDir = Path.of(properties.getBaseDir() == null ? "." : properties.getBaseDir())
                .toAbsolutePath()
                .normalize();
    }

    // ================= 加载入口 =================

    public LedConfig loadRoot() {
        Path p = baseDir.resolve("config/led.yml");
        if (!Files.exists(p)) {
            throw new ConfigException("缺少根配置: " + p);
        }
        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> root = new Yaml().load(in);
            return parseRoot(root);
        } catch (Exception e) {
            throw new ConfigException("解析 led.yml 失败: " + e.getMessage(), e);
        }
    }

    /** 加载全部屏定义（含 programs）。 */
    public List<Screen> loadScreens(LedConfig config) {
        Path dir = baseDir.resolve(config.getSystem().getScreensDir());
        if (!Files.isDirectory(dir)) {
            throw new ConfigException("屏定义目录不存在: " + dir);
        }
        List<Screen> screens = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            List<Path> files = stream.filter(f -> {
                        String n = f.getFileName().toString().toLowerCase();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
            for (Path f : files) {
                screens.add(loadScreen(f, config));
            }
        } catch (Exception e) {
            throw new ConfigException("读取屏定义目录失败: " + dir, e);
        }
        if (screens.isEmpty()) {
            throw new ConfigException("未找到任何屏定义文件: " + dir);
        }
        return screens;
    }

    /** 加载单个屏定义。 */
    public Screen loadScreen(Path file, LedConfig config) {
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> s = asMap(root.get("screen"), "screen");
            Screen screen = new Screen();
            screen.setId(str(s.get("id"), null));
            screen.setName(str(s.get("name"), screen.getId()));
            screen.setIp(str(s.get("ip"), null));
            screen.setWidth(intVal(s.get("width"), 0));
            screen.setHeight(intVal(s.get("height"), 0));
            screen.setColorType(intVal(s.get("colorType"), 0));
            screen.setGrayLevel(intVal(s.get("grayLevel"), 0));
            screen.setSaveType(s.get("saveType") != null ? intVal(s.get("saveType"), 0) : null);
            screen.setLedType(
                    s.get("ledType") != null
                            ? intVal(s.get("ledType"), config.getSdk().getLedType())
                            : config.getSdk().getLedType());
            screen.setRgb(
                    s.get("rgb") != null
                            ? intVal(s.get("rgb"), config.getSdk().getRgb())
                            : config.getSdk().getRgb());
            screen.setConnection(str(s.get("connection"), "tcp"));
            screen.setSdkProvider(str(s.get("sdkProvider"), null));

            require(screen.getId() != null && !screen.getId().isBlank(), "screen.id 必填: " + file);
            require(screen.getIp() != null && !screen.getIp().isBlank(), "screen.ip 必填: " + file);
            require(screen.getWidth() > 0 && screen.getHeight() > 0, "screen 宽高必须 >0: " + file);

            Object progs = root.get("programs");
            if (progs instanceof List<?> list) {
                for (Object o : list) {
                    screen.getPrograms().add(parseProgram(asMap(o, "program"), screen));
                }
            }
            parseScreenTuning(s, screen);
            validateScreen(screen, file);
            return screen;
        } catch (Exception e) {
            if (e instanceof ConfigException ce) {
                throw ce;
            }
            throw new ConfigException("解析屏定义失败 " + file + ": " + e.getMessage(), e);
        }
    }

    /** 解析屏级调参：tuning / scan / brightnessTimer / powerTimer / 搜卡参数。 */
    private void parseScreenTuning(Map<String, Object> s, Screen screen) {
        Map<String, Object> t = asMap(s.get("tuning"), "tuning");
        if (t != null) {
            Screen.ScreenTuning st = new Screen.ScreenTuning();
            st.setScreenName(str(t.get("screenName"), null));
            st.setBaud(t.get("baud") != null ? intVal(t.get("baud"), 115200) : null);
            st.setAdjustTime(bool(t.get("adjustTime"), false));
            st.setPowerOn(t.get("powerOn") != null ? bool(t.get("powerOn"), true) : null);
            st.setBrightness(t.get("brightness") != null ? intVal(t.get("brightness"), 8) : null);
            if (st.getBrightness() != null && (st.getBrightness() < 1 || st.getBrightness() > 16)) {
                throw new ConfigException("tuning.brightness 必须在 1-16: " + st.getBrightness());
            }
            screen.setTuning(st);
        }
        Map<String, Object> sc = asMap(s.get("scan"), "scan");
        if (sc != null) {
            Screen.ScanTuning st = new Screen.ScanTuning();
            st.setModuleWidth(sc.get("moduleWidth") != null ? intVal(sc.get("moduleWidth"), -1) : -1);
            st.setColumnDot(sc.get("columnDot") != null ? intVal(sc.get("columnDot"), 0) : 0);
            st.setScanStyle(sc.get("scanStyle") != null ? intVal(sc.get("scanStyle"), 0) : null);
            st.setDataHeight(sc.get("dataHeight") != null ? intVal(sc.get("dataHeight"), -1) : -1);
            st.setDbColor(sc.get("dbColor") != null ? intVal(sc.get("dbColor"), 0) : null);
            st.setDecode(sc.get("decode") != null ? intVal(sc.get("decode"), 0) : null);
            st.setOe(sc.get("oe") != null ? intVal(sc.get("oe"), 1) : null);
            st.setDa(sc.get("da") != null ? intVal(sc.get("da"), 1) : null);
            st.setRgSwap(sc.get("rgSwap") != null ? intVal(sc.get("rgSwap"), 0) : null);
            screen.setScan(st);
        }
        screen.setBrightnessTimer(parseIntGroups(s.get("brightnessTimer"), 5, "brightnessTimer"));
        screen.setPowerTimer(parseIntGroups(s.get("powerTimer"), 4, "powerTimer"));
        screen.setSearchBroadcast(bool(s.get("searchBroadcast"), true));
        screen.setSearchIniPath(str(s.get("searchIniPath"), null));
    }

    private List<int[]> parseIntGroups(Object o, int width, String field) {
        List<int[]> groups = new ArrayList<>();
        if (!(o instanceof List<?> list)) {
            return groups;
        }
        for (Object item : list) {
            if (!(item instanceof List<?> row) || row.size() != width) {
                throw new ConfigException(field + " 每组应为 " + width + " 个整数: " + item);
            }
            int[] g = new int[width];
            for (int i = 0; i < width; i++) {
                g[i] = intVal(row.get(i), 0);
            }
            groups.add(g);
        }
        if (groups.size() > 3) {
            throw new ConfigException(field + " 最多 3 组: " + groups.size());
        }
        return groups;
    }

    /** 加载单个屏的刷新配置；不存在返回 null。 */
    public RefreshConfig loadRefresh(String screenId, LedConfig config) {
        Path dir = baseDir.resolve(config.getSystem().getRefreshDir());
        for (String ext : new String[] {".yml", ".yaml"}) {
            Path p = dir.resolve(screenId + ext);
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    Map<String, Object> root = new Yaml().load(in);
                    RefreshConfig rc = parseRefresh(root);
                    for (RefreshConfig.Binding b : rc.getBindings()) {
                        if (b.getScreen() == null || b.getScreen().isBlank()) {
                            b.setScreen(screenId);
                        }
                    }
                    return rc;
                } catch (Exception e) {
                    throw new ConfigException("解析刷新配置失败 " + p + ": " + e.getMessage(), e);
                }
            }
        }
        return null;
    }

    // ================= 解析私有方法 =================

    private LedConfig parseRoot(Map<String, Object> root) {
        LedConfig c = new LedConfig();
        if (root == null) {
            return c;
        }
        Map<String, Object> sdk = asMap(root.get("sdk"), "sdk");
        if (sdk != null) {
            c.getSdk().setProvider(str(sdk.get("provider"), c.getSdk().getProvider()));
            c.getSdk().setDllPath(str(sdk.get("dllPath"), c.getSdk().getDllPath()));
            c.getSdk().setLedType(intVal(sdk.get("ledType"), c.getSdk().getLedType()));
            c.getSdk().setRgb(intVal(sdk.get("rgb"), c.getSdk().getRgb()));
            c.getSdk().setEncoding(str(sdk.get("encoding"), c.getSdk().getEncoding()));
            c.getSdk().setListenDllPath(str(sdk.get("listenDllPath"), c.getSdk().getListenDllPath()));
            c.getSdk().setListenPort(intVal(sdk.get("listenPort"), c.getSdk().getListenPort()));
        }
        Map<String, Object> sys = asMap(root.get("system"), "system");
        if (sys != null) {
            c.getSystem()
                    .setDefaultSaveType(
                            intVal(sys.get("defaultSaveType"), c.getSystem().getDefaultSaveType()));
            c.getSystem().setScreensDir(str(sys.get("screensDir"), c.getSystem().getScreensDir()));
            c.getSystem().setRefreshDir(str(sys.get("refreshDir"), c.getSystem().getRefreshDir()));
            c.getSystem()
                    .setContinueOnError(
                            bool(sys.get("continueOnError"), c.getSystem().isContinueOnError()));
            c.getSystem().setLogLevel(str(sys.get("logLevel"), c.getSystem().getLogLevel()));
        }
        parseSources(root, c);
        return c;
    }

    private void parseSources(Map<String, Object> root, LedConfig c) {
        Object sources = root.get("sources");
        if (!(sources instanceof Map<?, ?> m)) {
            return;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String id = String.valueOf(e.getKey());
            Map<String, Object> def = asMap(e.getValue(), "source " + id);
            if (def == null) {
                continue;
            }
            String type = str(def.get("type"), null);
            if (type == null || type.isBlank()) {
                throw new ConfigException("数据源 " + id + " 缺少 type");
            }
            def.remove("type");
            LedConfig.SourceDef sd = new LedConfig.SourceDef();
            sd.setType(type);
            sd.setParams(def);
            c.getSources().put(id, sd);
        }
    }

    private Program parseProgram(Map<String, Object> p, Screen screen) {
        Program prog = new Program();
        prog.setProgramNo(intVal(p.get("programNo"), 0));
        prog.setPlayTime(intVal(p.get("playTime"), 0));
        prog.setLoopCount(intVal(p.get("loopCount"), 1));

        Map<String, Object> sch = asMap(p.get("schedule"), "schedule");
        if (sch != null && bool(sch.get("enable"), false)) {
            Program.ProgramSchedule ps = new Program.ProgramSchedule();
            ps.setEnable(true);
            ps.setEnableFlag(intVal(sch.get("enableFlag"), ps.getEnableFlag()));
            ps.setWeekValue(intVal(sch.get("weekValue"), ps.getWeekValue()));
            ps.setStart(str(sch.get("start"), null));
            ps.setEnd(str(sch.get("end"), null));
            requireDateTime(ps.getStart(), "schedule.start");
            requireDateTime(ps.getEnd(), "schedule.end");
            prog.setSchedule(ps);
        }

        Object areas = p.get("areas");
        if (areas instanceof List<?> list) {
            for (Object o : list) {
                prog.getAreas().add(parseArea(asMap(o, "area"), screen));
            }
        }
        return prog;
    }

    private Area parseArea(Map<String, Object> a, Screen screen) {
        Area area = new Area();
        area.setAreaNo(intVal(a.get("areaNo"), 0));
        area.setType(str(a.get("type"), null));
        require(area.getType() != null && !area.getType().isBlank(), "area.type 必填");
        require(area.getAreaNo() >= 1 && area.getAreaNo() <= 255, "areaNo 必须在 1-255: " + area.getAreaNo());

        if (a.get("rect") instanceof List<?> r && r.size() == 4) {
            area.setRect(new Rect(intVal(r.get(0), 0), intVal(r.get(1), 0), intVal(r.get(2), 0), intVal(r.get(3), 0)));
        }
        area.setContent(str(a.get("content"), ""));
        area.setInStyle(intVal(a.get("inStyle"), 0));
        area.setSpeed(intVal(a.get("speed"), 10));
        area.setDelay(intVal(a.get("delay"), 0));
        area.setAddType(a.get("addType") != null ? intVal(a.get("addType"), 0) : null);
        area.setFontSize(a.get("fontSize") != null ? intVal(a.get("fontSize"), 16) : null);
        area.setFontColor(str(a.get("fontColor"), null));
        area.setNeimaIndex(a.get("neimaIndex") != null ? intVal(a.get("neimaIndex"), 0) : null);
        area.setAlignment(a.get("alignment") != null ? intVal(a.get("alignment"), 0) : null);
        area.setVcenter(a.get("vcenter") != null ? bool(a.get("vcenter"), false) : null);
        area.setBackColor(a.get("backColor") != null ? intVal(a.get("backColor"), 0) : null);
        area.setRotation(a.get("rotation") != null ? intVal(a.get("rotation"), 0) : null);
        area.setPlayCount(a.get("playCount") != null ? intVal(a.get("playCount"), 1) : null);
        area.setFilePath(str(a.get("filePath"), null));
        area.setLayout(a.get("layout") != null ? intVal(a.get("layout"), 1) : null);

        area.setShow(strList(a.get("show")));
        area.setDateFormat(a.get("dateFormat") != null ? intVal(a.get("dateFormat"), 0) : null);
        area.setWeekFormat(a.get("weekFormat") != null ? intVal(a.get("weekFormat"), 0) : null);
        area.setTimeFormat(a.get("timeFormat") != null ? intVal(a.get("timeFormat"), 0) : null);
        area.setMultiLine(a.get("multiLine") != null ? intVal(a.get("multiLine"), 0) : null);
        area.setDateColor(str(a.get("dateColor"), null));
        area.setWeekColor(str(a.get("weekColor"), null));
        area.setTimeColor(str(a.get("timeColor"), null));

        area.setShowStyle(a.get("showStyle") != null ? intVal(a.get("showStyle"), 0) : null);
        area.setCountMode(a.get("countMode") != null ? intVal(a.get("countMode"), 2) : null);
        area.setTxtStr(str(a.get("txtStr"), null));
        area.setEndTime(str(a.get("endTime"), null));
        if (area.getEndTime() != null) {
            requireDateTime(area.getEndTime(), "endTime");
        }
        area.setShowDay(a.get("showDay") != null ? bool(a.get("showDay"), true) : null);
        area.setShowHour(a.get("showHour") != null ? bool(a.get("showHour"), true) : null);
        area.setShowMinute(a.get("showMinute") != null ? bool(a.get("showMinute"), true) : null);
        area.setShowSecond(a.get("showSecond") != null ? bool(a.get("showSecond"), true) : null);

        area.setLowVersion(a.get("lowVersion") != null ? intVal(a.get("lowVersion"), 1) : null);
        area.setHighVersion(a.get("highVersion") != null ? intVal(a.get("highVersion"), 40) : null);
        area.setErrLevel(a.get("errLevel") != null ? intVal(a.get("errLevel"), 0) : null);
        area.setMaskVersion(a.get("maskVersion") != null ? intVal(a.get("maskVersion"), 0xff) : null);
        area.setBlankPointNum(a.get("blankPointNum") != null ? intVal(a.get("blankPointNum"), 4) : null);
        area.setBaseCodeColor(a.get("baseCodeColor") != null ? intVal(a.get("baseCodeColor"), 6) : null);
        area.setColorReverse(a.get("colorReverse") != null ? intVal(a.get("colorReverse"), 0) : null);
        area.setDefaultShow(a.get("defaultShow") != null ? intVal(a.get("defaultShow"), 1) : null);
        area.setDefaultCode(str(a.get("defaultCode"), null));

        area.setBorderType(a.get("borderType") != null ? intVal(a.get("borderType"), 0) : null);
        area.setBorderValue(a.get("borderValue") != null ? intVal(a.get("borderValue"), 0) : null);
        area.setBorderColor(a.get("borderColor") != null ? intVal(a.get("borderColor"), 0) : null);
        area.setBorderStyle(a.get("borderStyle") != null ? intVal(a.get("borderStyle"), 0) : null);
        area.setBorderSpeed(a.get("borderSpeed") != null ? intVal(a.get("borderSpeed"), 10) : null);

        if (a.get("font") instanceof Map<?, ?> f) {
            Area.Font font = new Area.Font();
            font.setName(str(f.get("name"), font.getName()));
            font.setSize(intVal(f.get("size"), font.getSize()));
            font.setColor(str(f.get("color"), font.getColor()));
            font.setBold(bool(f.get("bold"), false));
            font.setItalic(bool(f.get("italic"), false));
            font.setUnderline(bool(f.get("underline"), false));
            area.setFont(font);
        }
        return area;
    }

    private RefreshConfig parseRefresh(Map<String, Object> root) {
        RefreshConfig rc = new RefreshConfig();
        if (root == null) {
            return rc;
        }
        rc.setInterval(str(root.get("interval"), rc.getInterval()));
        Object bindings = root.get("bindings");
        if (bindings instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> b = asMap(o, "binding");
                RefreshConfig.Binding binding = new RefreshConfig.Binding();
                binding.setScreen(str(b.get("screen"), null));
                binding.setProgramNo(b.get("programNo") != null ? intVal(b.get("programNo"), 0) : null);
                binding.setAreaIndex(intVal(b.get("areaIndex"), 0));
                binding.setColor(str(b.get("color"), binding.getColor()));
                binding.setEffect(intVal(b.get("effect"), binding.getEffect()));
                binding.setTemplate(str(b.get("template"), binding.getTemplate()));
                binding.setSource(str(b.get("source"), binding.getSource()));
                binding.setCounterStart(b.get("counterStart") != null ? doubleVal(b.get("counterStart"), 0) : null);
                binding.setCounterStep(b.get("counterStep") != null ? doubleVal(b.get("counterStep"), 1) : null);
                binding.setCounterDecimals(
                        b.get("counterDecimals") != null ? intVal(b.get("counterDecimals"), 0) : null);
                binding.setNowPattern(str(b.get("nowPattern"), binding.getNowPattern()));
                binding.setHttpUrl(str(b.get("httpUrl"), null));
                binding.setHttpField(str(b.get("httpField"), null));
                binding.setScript(str(b.get("script"), null));
                rc.getBindings().add(binding);
            }
        }
        return rc;
    }

    // ================= 校验 =================

    private void validateScreen(Screen screen, Path file) {
        Set<Integer> areaNos = new HashSet<>();
        for (Program prog : screen.getPrograms()) {
            int autoNeimaIndex = 0;
            Set<Integer> neimaIndexes = new HashSet<>();

            for (Area a : prog.getAreas()) {
                if (!areaNos.add(a.getAreaNo())) {
                    throw new ConfigException("区域号重复: " + a.getAreaNo() + " @ " + file);
                }
                if (a.getRect() != null && !screen.contains(a.getRect())) {
                    throw new ConfigException("区域超出屏范围 " + a.getRect() + " 屏=" + screen.getWidth() + "x"
                            + screen.getHeight() + " areaNo=" + a.getAreaNo() + " @ " + file);
                }
                validateArea(a, screen, file);

                if ("neima".equalsIgnoreCase(a.getType())) {
                    if (a.getNeimaIndex() == null) {
                        a.setNeimaIndex(autoNeimaIndex);
                    }
                    autoNeimaIndex++;
                    if (!neimaIndexes.add(a.getNeimaIndex())) {
                        throw new ConfigException("内码区顺序号重复: " + a.getNeimaIndex() + " @ " + file);
                    }
                }
            }

            for (int i = 0; i < prog.getAreas().size(); i++) {
                for (int j = i + 1; j < prog.getAreas().size(); j++) {
                    Area a1 = prog.getAreas().get(i);
                    Area a2 = prog.getAreas().get(j);
                    Rect r1 = a1.getRect();
                    Rect r2 = a2.getRect();
                    if (r1 == null || r2 == null || !r1.overlaps(r2)) {
                        continue;
                    }
                    boolean a1Back = "image".equalsIgnoreCase(a1.getType()) && isBackground(a1);
                    boolean a2Back = "image".equalsIgnoreCase(a2.getType()) && isBackground(a2);
                    boolean a1Border = "border".equalsIgnoreCase(a1.getType());
                    boolean a2Border = "border".equalsIgnoreCase(a2.getType());
                    if (a1Back || a2Back || a1Border || a2Border) {
                        continue;
                    }
                    throw new ConfigException("区域重叠: areaNo " + a1.getAreaNo() + " 与 " + a2.getAreaNo() + " @ " + file);
                }
            }
        }
    }

    private static boolean isBackground(Area a) {
        Integer layout = a.getLayout();
        return layout != null && layout == 0;
    }

    private void validateArea(Area a, Screen screen, Path file) {
        String t = a.getType().toLowerCase();
        switch (t) {
            case "neima" -> {
                if (a.getFontSize() != null
                        && a.getFontSize() != 16
                        && a.getFontSize() != 24
                        && a.getFontSize() != 32) {
                    throw new ConfigException("内码区 fontSize 只能是 16/24/32: " + a.getFontSize() + " @ " + file);
                }
                if (a.getInStyle() < 0 || a.getInStyle() > 11) {
                    throw new ConfigException("内码区 inStyle 只支持 0-11: " + a.getInStyle() + " @ " + file);
                }
                if (a.getNeimaIndex() != null && (a.getNeimaIndex() < 0 || a.getNeimaIndex() >= 32)) {
                    throw new ConfigException("内码区 neimaIndex 只支持 0-31: " + a.getNeimaIndex() + " @ " + file);
                }
            }
            case "multiline", "singleline", "static" -> {
                if (a.getInStyle() < 0 || a.getInStyle() > 38) {
                    throw new ConfigException("文本区 inStyle 只支持 0-38: " + a.getInStyle() + " @ " + file);
                }
                if (a.getAlignment() != null && (a.getAlignment() < 0 || a.getAlignment() > 2)) {
                    throw new ConfigException("alignment 只支持 0/1/2: " + a.getAlignment() + " @ " + file);
                }
            }
            case "image" -> {
                if (a.getFilePath() == null || a.getFilePath().isBlank()) {
                    throw new ConfigException("image 区必须指定 filePath @ " + file);
                }
                if (a.getInStyle() < 0 || a.getInStyle() > 38) {
                    throw new ConfigException("图片区 inStyle 只支持 0-38: " + a.getInStyle() + " @ " + file);
                }
            }
            case "qrcode" -> {
                if (a.getLowVersion() != null && (a.getLowVersion() < 1 || a.getLowVersion() > 40)) {
                    throw new ConfigException("lowVersion 只支持 1-40: " + a.getLowVersion() + " @ " + file);
                }
                if (a.getHighVersion() != null && (a.getHighVersion() < 1 || a.getHighVersion() > 40)) {
                    throw new ConfigException("highVersion 只支持 1-40: " + a.getHighVersion() + " @ " + file);
                }
            }
            case "clock" -> {
                if (a.getDateFormat() != null && (a.getDateFormat() < 0 || a.getDateFormat() > 7)) {
                    throw new ConfigException("dateFormat 只支持 0-7: " + a.getDateFormat() + " @ " + file);
                }
                if (a.getWeekFormat() != null && (a.getWeekFormat() < 0 || a.getWeekFormat() > 2)) {
                    throw new ConfigException("weekFormat 只支持 0-2: " + a.getWeekFormat() + " @ " + file);
                }
                if (a.getTimeFormat() != null && (a.getTimeFormat() < 0 || a.getTimeFormat() > 6)) {
                    throw new ConfigException("timeFormat 只支持 0-6: " + a.getTimeFormat() + " @ " + file);
                }
            }
            case "time" -> {
                if (a.getEndTime() == null || a.getEndTime().isBlank()) {
                    throw new ConfigException("time 区必须指定 endTime @ " + file);
                }
                if (a.getShowStyle() != null && (a.getShowStyle() < 0 || a.getShowStyle() > 4)) {
                    throw new ConfigException("showStyle 只支持 0-4: " + a.getShowStyle() + " @ " + file);
                }
                if (a.getCountMode() != null && (a.getCountMode() < 1 || a.getCountMode() > 2)) {
                    throw new ConfigException("countMode 只支持 1(正计时)/2(倒计时): " + a.getCountMode() + " @ " + file);
                }
            }
            case "voice", "border" -> {
                // 无额外校验
            }
            default -> throw new ConfigException("不支持的区域类型: " + a.getType() + " @ " + file);
        }

        if (a.getRect() != null && screen.getLedType() != 3 && screen.getLedType() != 4) {
            if (a.getRect().getLeft() % 8 != 0 || a.getRect().getTop() % 8 != 0) {
                throw new ConfigException("非 C/E5/E6/C8 卡的区域坐标必须为 8 的整数倍: " + a.getRect() + " @ " + file);
            }
        }
    }

    private void requireDateTime(String text, String field) {
        try {
            LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ConfigException(field + " 需要格式 yyyy-MM-dd HH:mm:ss: " + text);
        }
    }

    // ================= 工具 =================

    private static Map<String, Object> asMap(Object o, String name) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) m;
            return r;
        }
        throw new ConfigException(name + " 应为映射结构");
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static List<String> strList(Object o) {
        List<String> r = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x != null) {
                    r.add(x.toString());
                }
            }
        } else if (o != null && !o.toString().isBlank()) {
            for (String s : o.toString().split("[,，\\s]+")) {
                if (!s.isBlank()) {
                    r.add(s);
                }
            }
        }
        return r;
    }

    private static int intVal(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(o.toString().trim());
    }

    private static double doubleVal(Object o, double def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(o.toString().trim());
    }

    private static boolean bool(Object o, boolean def) {
        return o == null ? def : Boolean.parseBoolean(o.toString().trim());
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new ConfigException(msg);
        }
    }
}
