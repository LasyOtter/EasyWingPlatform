package com.easywing.platform.ai.artifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从会话消息中识别 ```html / react / mermaid``` 等围栏代码块，提取为可运行产物。
 * <p>
 * 只提取受支持的类型，忽略语言标记大小写差异；同一消息内按出现顺序编号。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class ArtifactExtractor {

    private static final Pattern FENCE = Pattern.compile("(?s)```([A-Za-z0-9_+#-]*)[ \\t]*\\r?\\n(.*?)```");

    /** 围栏语言标记 → 产物类型 */
    private static final Map<String, Artifact.Type> SUPPORTED = Map.of(
            "html", Artifact.Type.HTML,
            "htm", Artifact.Type.HTML,
            "react", Artifact.Type.REACT,
            "jsx", Artifact.Type.REACT,
            "tsx", Artifact.Type.REACT,
            "mermaid", Artifact.Type.MERMAID);

    private ArtifactExtractor() {}

    /**
     * 提取一条消息中的产物代码块。
     */
    public static List<Extracted> extract(String content) {
        List<Extracted> extracted = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return extracted;
        }
        Matcher matcher = FENCE.matcher(content);
        while (matcher.find()) {
            String language =
                    matcher.group(1) == null ? "" : matcher.group(1).trim().toLowerCase(Locale.ROOT);
            Artifact.Type type = SUPPORTED.get(language);
            if (type == null) {
                continue;
            }
            String code = matcher.group(2) == null ? "" : matcher.group(2).strip();
            if (code.length() < 2) {
                continue;
            }
            extracted.add(new Extracted(type, language, code));
        }
        return extracted;
    }

    public record Extracted(Artifact.Type type, String language, String code) {

        /** 取首行作为标题，便于列表展示 */
        public String title() {
            String firstLine = code.lines().findFirst().orElse("").strip();
            return firstLine.length() > 60 ? firstLine.substring(0, 60) + "…" : firstLine;
        }
    }
}
