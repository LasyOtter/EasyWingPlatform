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
package com.easywing.platform.aid.ffmpeg;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间轴上的一个分镜片段（PRD FR-7 时间轴与成片导出）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineSegment {

    /** 分镜 ID，用于回填结果 */
    private Long shotId;

    /** 视频片段在自有存储上的绝对路径或 URI */
    private String videoPath;

    /** 该片段期望时长（秒）；≤0 时使用源时长 */
    @Builder.Default
    private double durationSeconds = 0D;

    /** 配音音频路径；null 表示该镜无台词 */
    private String voicePath;

    /** 该配音相对片头的延迟毫秒数（台词对齐） */
    @Builder.Default
    private long voiceDelayMs = 0L;

    /** 字幕文本（可选，烧录到画面） */
    private String subtitle;

    /** 片头字幕起始时间偏移（毫秒），默认与 voiceDelayMs 一致 */
    private Long subtitleOffsetMs;

    /**
     * 本片段的配音延迟是否为显式指定。
     *
     * @return true 表示调用方已给定延迟
     */
    public boolean hasExplicitVoiceDelay() {
        return voiceDelayMs > 0;
    }

    /** 创建时间轴所需的字幕条目。 */
    public static List<SubtitleEntry> toSubtitles(List<TimelineSegment> segments) {
        List<SubtitleEntry> entries = new ArrayList<>();
        long cursorMs = 0;
        int index = 1;
        for (TimelineSegment segment : segments) {
            if (segment.getSubtitle() != null && !segment.getSubtitle().isBlank()) {
                long offset = segment.getSubtitleOffsetMs() != null
                        ? segment.getSubtitleOffsetMs()
                        : segment.getVoiceDelayMs();
                long start = cursorMs + offset;
                long end = start + Math.max(500, (long) (segment.getDurationSeconds() * 1000) - 200);
                entries.add(new SubtitleEntry(index++, start, end, segment.getSubtitle()));
            }
            cursorMs += (long) (segment.getDurationSeconds() * 1000);
        }
        return entries;
    }

    /** SRT 字幕条目。 */
    public record SubtitleEntry(int order, long startMs, long endMs, String text) {

        /** 输出为 SRT 格式行块。 */
        public String toSrtBlock() {
            return order + "\n" + format(startMs) + " --> " + format(endMs) + "\n" + text + "\n";
        }

        private static String format(long ms) {
            long total = Math.max(0, ms);
            long hours = total / 3_600_000;
            long minutes = (total % 3_600_000) / 60_000;
            long seconds = (total % 60_000) / 1_000;
            long millis = total % 1_000;
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
        }
    }
}
