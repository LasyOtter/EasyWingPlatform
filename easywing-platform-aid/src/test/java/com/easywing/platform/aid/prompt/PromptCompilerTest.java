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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 提示词编译器测试（FR-4.7 / FR-11.3 / FR-11.6 ~ FR-11.8，NFR-13）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class PromptCompilerTest {

    private VisualSnapshot snapshot;
    private PromptCompiler compiler;
    private PromptCompiler.AssetResolver resolver;

    @BeforeEach
    void setUp() {
        compiler = new PromptCompiler();
        snapshot = VisualSnapshot.builder()
                .styleDescription("厚涂写实国风，电影级光影")
                .colorPalette(List.of("玄青 #1A2E4A", "朱砂 #C0392B"))
                .subStyles(List.of("国漫", "奇幻"))
                .constraints(List.of("主体居中构图", "保持角色五官一致"))
                .negativeWords(List.of("模糊", "变形手"))
                .aspectRatio("9:16")
                .snapshotTime(1_700_000_000_000L)
                .version(3)
                .build();
        resolver = key -> switch (key) {
            case "C01" -> "C01:夸父·标准像(角色一致性主参考)";
            case "S01" -> "S01:大荒原·烈日正打(场景参考)";
            default -> null;
        };
    }

    @Test
    void injectsStyleBlockAndPalette() {
        var result = compiler.compileImage(snapshot, "夸父站在大荒原上", null, resolver);

        String prompt = result.finalPrompt();
        assertTrue(prompt.contains("厚涂写实国风"), prompt);
        assertTrue(prompt.contains("玄青 #1A2E4A, 朱砂 #C0392B"), prompt);
        assertTrue(prompt.contains("夸父站在大荒原上"), prompt);
        assertFalse(result.hasWarnings());
    }

    /** FR-11.6：色盘必须逐字出现在最终提示词中。 */
    @Test
    void paletteIsRenderedVerbatim() {
        var result = compiler.compileImage(snapshot, "画面描述", null, resolver);
        for (String color : snapshot.getColorPalette()) {
            assertTrue(result.finalPrompt().contains(color), "缺少色板项: " + color);
        }
    }

    @Test
    void resolvesAssetRefToReadableAnnotation() {
        var result = compiler.compileImage(snapshot, "@资产[C01] 手持杖", null, resolver);

        assertTrue(result.finalPrompt().contains("[C01:夸父·标准像(角色一致性主参考)]"));
        assertEquals(List.of("C01"), result.referenceKeys());
    }

    /** FR-11.3：重复引用在审计键中去重，但文本保留用户原始表述。 */
    @Test
    void dedupesReferenceKeysButKeepsTextOccurrences() {
        var result = compiler.compileImage(snapshot, "@资产[C01] 转身，@资产[C01] 凝重", null, resolver);

        assertEquals(List.of("C01"), result.referenceKeys());
        assertEquals(2, result.finalPrompt().split("C01:夸父", -1).length - 1, "正文中的两次引用都应保留可读标注");
    }

    @Test
    void warnsOnUnresolvedAssetRef() {
        var result = compiler.compileImage(snapshot, "@资产[NOPE]", null, resolver);

        assertTrue(result.hasWarnings());
        assertTrue(result.warnings().get(0).contains("NOPE"));
        assertTrue(result.referenceKeys().isEmpty());
    }

    /** FR-11.8：禁用词命中只告警，不静默改写用户正文。 */
    @Test
    void warnsOnBannedWordWithoutRewritingContent() {
        var result = compiler.compileImage(snapshot, "画面有些模糊，手略微变形手", null, resolver);

        assertTrue(result.hasWarnings());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("模糊")));
        assertTrue(result.finalPrompt().contains("画面有些模糊"), "不应静默删除禁用词内容");
    }

    /** NFR-13 / AC-15：审计元数据须记录各字段来源与快照版本。 */
    @Test
    void auditMetadataCapturesFieldProvenance() {
        var result = compiler.compileVideo(snapshot, "@资产[S01] 落日", null, resolver);

        Map<String, Object> audit = result.audit();
        assertEquals("厚涂写实国风，电影级光影", audit.get("styleBlock"));
        assertEquals("玄青 #1A2E4A, 朱砂 #C0392B", audit.get("palette"));
        assertEquals("9:16", audit.get("aspect"));
        assertEquals(3, audit.get("snapshotVersion"));
        assertEquals(List.of("S01"), audit.get("assetKeys"));
        assertNotNull(audit.get("rawContent"), "需保留原文以支持回放");
    }

    @Test
    void videoTemplateMentionsAspectAndFirstFrame() {
        var result = compiler.compileVideo(snapshot, "夸父奔跑", null, resolver);

        assertTrue(result.finalPrompt().contains("9:16"), result.finalPrompt());
        assertTrue(result.finalPrompt().toLowerCase().contains("首帧"), result.finalPrompt());
    }

    @Test
    void customTemplateOverridesDefault() {
        String template = "[风格]{{style}}\n[色盘]{{palette}}\n[内容]{{content}}";
        var result = compiler.compileImage(snapshot, "测试", template, resolver);

        assertTrue(result.finalPrompt().startsWith("[风格]厚涂写实国风"), result.finalPrompt());
        assertTrue(result.finalPrompt().contains("\n[色盘]玄青 #1A2E4A"), result.finalPrompt());
    }

    /** 空快照不应导致 NPE，输出仍应包含内容主体。 */
    @Test
    void toleratesNullSnapshot() {
        var result = compiler.compileImage(null, "纯文字描述", null, resolver);

        assertTrue(result.finalPrompt().contains("纯文字描述"));
    }

    @Test
    void collapsesExcessiveBlankLines() {
        var result = compiler.compileImage(
                VisualSnapshot.builder().styleDescription("S").build(), "C", "{{style}}\n\n\n\n{{content}}", resolver);
        assertFalse(result.finalPrompt().contains("\n\n\n"), result.finalPrompt());
    }
}
