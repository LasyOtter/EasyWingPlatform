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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词中的资产占位符解析（PRD FR-4.4 / FR-11.3）。
 * <p>
 * 支持两种语法：{@code @资产[C01]} 与 {@code @图片1[场景参考]}。前者按编号引用，
 * 后者是用户在 UI 中选择的「第 N 张图 + 名称」，两者都归一到资产键。
 * <p>
 * 占位符是「提示词文本层」与「模型参数层」的解耦点：文本层只保留可读标注，
 * 真实参考图 URL 由 Provider 适配层在提交上游前注入，禁止前端拼接最终模型参数。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class AssetRefPattern {

    /** 匹配 {@code @资产[...]} 与 {@code @图片N[...]}，捕获括号内的键名。 */
    public static final Pattern PATTERN = Pattern.compile("@(?:资产|图片\\d*)\\[([^\\]]+)\\]");

    private AssetRefPattern() {}

    /**
     * 提取提示词中引用的资产键，按首次出现顺序去重。
     *
     * @param prompt 待解析的提示词正文
     * @return 去重后的资产键列表（可为空，不为 null）
     */
    public static List<String> extractKeys(String prompt) {
        Set<String> keys = new LinkedHashSet<>();
        if (prompt == null || prompt.isEmpty()) {
            return new ArrayList<>();
        }
        Matcher matcher = PATTERN.matcher(prompt);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * 判断提示词是否包含任何资产占位符。
     *
     * @param prompt 提示词正文
     * @return 含占位符返回 {@code true}
     */
    public static boolean containsRef(String prompt) {
        return prompt != null && PATTERN.matcher(prompt).find();
    }

    /**
     * 把占位符替换为给定的渲染函数输出。
     *
     * @param prompt   提示词正文
     * @param renderer 接收资产键、返回替换文本；返回 null 时回退为键名本身
     * @return 替换后的提示词
     */
    public static String render(String prompt, KeyRenderer renderer) {
        if (prompt == null || prompt.isEmpty()) {
            return prompt;
        }
        Matcher matcher = PATTERN.matcher(prompt);
        StringBuilder sb = new StringBuilder(prompt.length());
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement = renderer.render(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : key));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 占位符渲染函数。 */
    @FunctionalInterface
    public interface KeyRenderer {
        String render(String key);
    }
}
