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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 资产占位符解析测试（FR-4.4 / FR-11.3）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class AssetRefPatternTest {

    @Test
    void extractsSingleRef() {
        assertEquals(List.of("C01"), AssetRefPattern.extractKeys("夸父站在大荒原上 @资产[C01]"));
    }

    @Test
    void extractsMultipleRefsInOrder() {
        assertEquals(List.of("C01", "S01", "P01"), AssetRefPattern.extractKeys("@资产[C01] 站在 @资产[S01] 手持 @资产[P01]"));
    }

    @Test
    void dedupesRepeatedRefKeepingFirstOrder() {
        // FR-11.3：同一参考图多处引用需去重
        assertEquals(List.of("C01"), AssetRefPattern.extractKeys("@资产[C01] 转身，@资产[C01] 表情凝重"));
        assertEquals(List.of("C01", "S01"), AssetRefPattern.extractKeys("@资产[C01] @资产[S01] @资产[C01]"));
    }

    @Test
    void supportsNumberedImageSyntax() {
        assertEquals(List.of("场景参考"), AssetRefPattern.extractKeys("@图片1[场景参考]"));
        assertEquals(List.of("角色主参考"), AssetRefPattern.extractKeys("@图片[角色主参考]"));
    }

    @Test
    void handlesBlankAndNull() {
        assertTrue(AssetRefPattern.extractKeys(null).isEmpty());
        assertTrue(AssetRefPattern.extractKeys("").isEmpty());
        assertTrue(AssetRefPattern.extractKeys("没有占位符的提示词").isEmpty());
        assertFalse(AssetRefPattern.containsRef("没有占位符"));
        assertTrue(AssetRefPattern.containsRef("@资产[C01]"));
    }

    @Test
    void trimsWhitespaceInsideBrackets() {
        assertEquals(List.of("C01"), AssetRefPattern.extractKeys("@资产[ C01 ]"));
    }

    /** renderer 的返回值按字面替换，本层不额外包裹——可读标注由调用方拼装。 */
    @Test
    void rendersWithCustomRenderer() {
        String out = AssetRefPattern.render("@资产[C01] 与 @资产[NOPE]", key -> "KEY-" + key);
        assertEquals("KEY-C01 与 KEY-NOPE", out);
    }

    @Test
    void fallsBackToKeyWhenRendererReturnsNull() {
        String out = AssetRefPattern.render("@资产[C01] @资产[X]", key -> null);
        assertEquals("C01 X", out);
    }

    @Test
    void replacesOnlyPlaceholdersLeavingRestUntouched() {
        String source = "画面：@资产[C01]。备注：@@不是占位符 [C01]";
        String out = AssetRefPattern.render(source, key -> "[" + key + "]");
        assertEquals("画面：[C01]。备注：@@不是占位符 [C01]", out);
    }
}
