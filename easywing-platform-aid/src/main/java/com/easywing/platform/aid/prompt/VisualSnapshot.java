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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目级视觉设定快照（PRD FR-11.2 / NFR-12）。
 * <p>
 * 是提示词拼接的单一事实来源：风格描述、色盘、子风格、约束条件、禁用词、画幅。
 * 必须可完整序列化为 JSON 以支持「导出模板 → 新项目导入」（AC-11），
 * 因此在 JSON 反序列化场景下不允许丢弃字段，且所有集合默认非 null。
 * <p>该快照在生成任务提交时被复制一份随任务落库，以保证历史任务可追溯到当时的设定
 * （NFR-13 提示词审计、AC-15）。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisualSnapshot {

    /** 风格描述主文本（硬约束层的内容锚） */
    private String styleDescription;

    /**
     * 色盘声明（中文 + 十六进制），逐字引用到每条提示词（FR-11.6）。
     * <p>示例：{@code 玄青 #1A2E4A}。顺序即渲染顺序。</p>
     */
    @Builder.Default
    private List<String> colorPalette = new ArrayList<>();

    /** 子风格标签族，用于长提示词的段落强化（FR-11.7） */
    @Builder.Default
    private List<String> subStyles = new ArrayList<>();

    /** 约束条件（强命令式正向要求） */
    @Builder.Default
    private List<String> constraints = new ArrayList<>();

    /** 禁用词/负向清单，提交前扫描告警（FR-11.8） */
    @Builder.Default
    private List<String> negativeWords = new ArrayList<>();

    /** 画幅，如 {@code 9:16} / {@code 16:9} */
    private String aspectRatio;

    /** 快照时间戳（毫秒），用于审计与阶段门失效判定 */
    @Builder.Default
    private long snapshotTime = 0L;

    /** 快照版本号，每次修增 +1；下游产物记录该版本以判定是否需要失效 */
    @Builder.Default
    private int version = 1;

    /** 尾帧描述规范（FR-11.9）：主体 / 背景 / 光线方向 / 构图 / 氛围 */
    private TailFrameSpec tailFrameSpec;

    /**
     * 返回可直接拼入提示词的色盘文本。
     *
     * @return 逗号分隔的色板文本；空色板返回空串
     */
    public String paletteText() {
        return colorPalette == null ? "" : String.join(", ", colorPalette);
    }

    /**
     * 深拷贝，供任务提交时固化快照使用。
     *
     * @return 独立副本
     */
    public VisualSnapshot copy() {
        return VisualSnapshot.builder()
                .styleDescription(styleDescription)
                .colorPalette(colorPalette == null ? new ArrayList<>() : new ArrayList<>(colorPalette))
                .subStyles(subStyles == null ? new ArrayList<>() : new ArrayList<>(subStyles))
                .constraints(constraints == null ? new ArrayList<>() : new ArrayList<>(constraints))
                .negativeWords(negativeWords == null ? new ArrayList<>() : new ArrayList<>(negativeWords))
                .aspectRatio(aspectRatio)
                .snapshotTime(snapshotTime)
                .version(version)
                .tailFrameSpec(tailFrameSpec)
                .build();
    }

    /** 尾帧结构化描述规范（FR-11.9 / AC-13 集间连续性）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TailFrameSpec {
        private String subject;
        private String background;
        private String lightDirection;
        private String composition;
        private String mood;
        /** 生成时必须包含的色板色名 */
        private List<String> requiredColorNames;
    }
}
