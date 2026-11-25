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
package com.easywing.platform.aid.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 提示词编译器（PRD FR-4.4 / FR-4.7 / FR-11.3 / FR-11.6 ~ FR-11.8，NFR-13）。
 * <p>
 * 职责边界明确：
 * <ul>
 *   <li><b>只做文本层编译</b>：输出最终提示词 + 审计元数据 + 参考图键清单。</li>
 *   <li><b>不碰模型参数层</b>：参考图真实 URL、分辨率、时长等由 Provider 适配层注入，
 *       因此本类产出 {@link CompileResult#referenceKeys} 而非 URL 列表。</li>
 *   <li><b>不改写用户正文</b>：占位符只被替换为可读标注（供人工与上游理解），
 *       原文另存于审计字段，便于回溯。</li>
 * </ul>
 * <p>模板占位符支持 {@code {{style}}}、{@code {{substyles}}}、{@code {{palette}}}、
 * {@code {{aspect}}}、{@code {{content}}}、{@code {{constraints}}}、{@code {{negative}}}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
public class PromptCompiler {

    /** 默认图片提示词模板：风格块 → 色盘 → 内容 → 约束 → 负向。 */
    public static final String DEFAULT_IMAGE_TEMPLATE =
            "{{style}}\n\n{{substyles}}\n\n{{palette}}\n\n{{content}}\n\n{{constraints}}";

    /** 默认视频提示词模板：在图片模板基础上追加画幅与时长锚点。 */
    public static final String DEFAULT_VIDEO_TEMPLATE = "{{style}}\n\n{{substyles}}\n\n{{palette}}\n\n{{content}}\n\n"
            + "画面比例 {{aspect}}，镜头运动平稳，首帧与参考图保持一致。\n\n{{constraints}}";

    /**
     * 编译图片生成提示词。
     *
     * @param snapshot      项目视觉设定快照
     * @param content       分镜画面描述（可含 {@code @资产[..]} 占位符）
     * @param template      提示词模板，null 时用默认模板
     * @param assetResolver 资产键 → 可读标注的解析器；返回 null 表示未解析
     * @return 编译结果（含最终提示词、审计信息、告警）
     */
    public CompileResult compileImage(
            VisualSnapshot snapshot, String content, String template, AssetResolver assetResolver) {
        return compile(snapshot, content, template, DEFAULT_IMAGE_TEMPLATE, assetResolver);
    }

    /**
     * 编译视频生成提示词。
     *
     * @param snapshot      项目视觉设定快照
     * @param content       分镜画面/动作描述（可含 {@code @资产[..]} 占位符）
     * @param template      提示词模板，null 时用默认模板
     * @param assetResolver 资产键 → 可读标注的解析器
     * @return 编译结果
     */
    public CompileResult compileVideo(
            VisualSnapshot snapshot, String content, String template, AssetResolver assetResolver) {
        return compile(snapshot, content, template, DEFAULT_VIDEO_TEMPLATE, assetResolver);
    }

    private CompileResult compile(
            VisualSnapshot snapshot,
            String content,
            String customTemplate,
            String defaultTemplate,
            AssetResolver assetResolver) {

        List<String> warnings = new ArrayList<>();
        String template = (customTemplate == null || customTemplate.isBlank()) ? defaultTemplate : customTemplate;

        // FR-11.3 提取引用并按需裁剪/告警
        List<String> referencedKeys = AssetRefPattern.extractKeys(content);
        List<String> resolvedKeys = new ArrayList<>();
        for (String key : referencedKeys) {
            if (assetResolver == null || assetResolver.resolveAnnotation(key) != null) {
                resolvedKeys.add(key);
            } else {
                warnings.add("提示词引用了未登记的资产「" + key + "」");
            }
        }

        // FR-4.7 / FR-11.6：占位符替换为可读标注，色盘逐字引用
        String annotatedContent = AssetRefPattern.render(content, key -> {
            String annotation = assetResolver == null ? null : assetResolver.resolveAnnotation(key);
            return annotation != null ? "[" + annotation + "]" : "[" + key + "]";
        });

        String negativeText = snapshot == null || snapshot.getNegativeWords() == null
                ? ""
                : String.join(", ", snapshot.getNegativeWords());

        // FR-11.8：禁用词扫描（对用户正文，不做静默改写）
        if (snapshot != null && snapshot.getNegativeWords() != null) {
            for (String banned : snapshot.getNegativeWords()) {
                if (banned != null && !banned.isBlank() && content != null && content.contains(banned)) {
                    warnings.add("画面描述命中禁用词「" + banned + "」，建议修改后重新生成");
                }
            }
        }

        VisualSnapshot safe =
                snapshot != null ? snapshot : VisualSnapshot.builder().build();

        String finalPrompt = template.replace("{{style}}", nullToEmpty(safe.getStyleDescription()))
                .replace("{{substyles}}", join(safe.getSubStyles()))
                .replace("{{palette}}", safe.paletteText())
                .replace("{{aspect}}", nullToEmpty(safe.getAspectRatio()))
                .replace("{{content}}", annotatedContent)
                .replace("{{constraints}}", join(safe.getConstraints()))
                .replace("{{negative}}", negativeText)
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("styleBlock", safe.getStyleDescription());
        audit.put("subStyles", safe.getSubStyles());
        audit.put("palette", safe.paletteText());
        audit.put("aspect", safe.getAspectRatio());
        audit.put("constraints", safe.getConstraints());
        audit.put("negativeWords", negativeText);
        audit.put("assetKeys", resolvedKeys);
        audit.put("rawContent", content);
        audit.put("template", template);
        if (safe.getSnapshotTime() > 0) {
            audit.put("snapshotVersion", safe.getVersion());
            audit.put("snapshotTime", safe.getSnapshotTime());
        }

        log.debug(
                "提示词编译完成: refs={}, warnings={}, chars={}", resolvedKeys.size(), warnings.size(), finalPrompt.length());

        return new CompileResult(finalPrompt, resolvedKeys, audit, warnings);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("; ", values);
    }

    /**
     * 编译结果。
     *
     * @param finalPrompt   最终提交给 Provider 的提示词全文
     * @param referenceKeys 解析出的资产键清单（顺序即去重后的首次出现顺序），
     *                      由 Provider 适配层据此组装真实参考图参数
     * @param audit         审计元数据（风格块/色盘/约束/模板/快照版本），随任务落库（NFR-13）
     * @param warnings      需要展示给用户的告警（禁用词、未知引用、裁剪等）
     */
    public record CompileResult(
            String finalPrompt, List<String> referenceKeys, Map<String, Object> audit, List<String> warnings) {

        /** 是否存在需要提示用户的告警。 */
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }

    /** 资产键解析器：返回用于提示词文本层的可读标注。 */
    @FunctionalInterface
    public interface AssetResolver {
        /**
         * @param key 资产键（编号或名称）
         * @return 可读标注（如 {@code C01:夸父·标准像(角色一致性主参考)}）；未登记返回 null
         */
        String resolveAnnotation(String key);
    }
}
