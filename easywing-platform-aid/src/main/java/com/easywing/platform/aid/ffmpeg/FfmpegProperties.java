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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FFmpeg 集成配置（{@code easywing.aid.ffmpeg.*}）。
 * <p>
 * M0 预研在本机实测：FFmpeg N-115858（gcc 13.2，含 libx264 / libfribidi / libass /
 * libopenjpeg / libopus）可完成分段拼接、统一重编码、多音轨 adelay 对位、
 * SRT/ASS 字幕烧录与首尾帧抽取；合成 120 秒视频约 0.5s。
 * </p>
 * <p>{@code workDir} 为相对路径字幕文件的解析基准——FFmpeg 的 subtitles 过滤器以
 * {@code :} 作为选项分隔符，Windows 盘符会被误解析，因此实现统一在 workDir 下用相对文件名。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.aid.ffmpeg")
public class FfmpegProperties {

    /** 是否启用 FFmpeg 合成能力。 */
    private boolean enabled = true;

    /** ffmpeg 可执行文件路径，空则从 PATH 查找 {@code ffmpeg}。 */
    private String binaryPath = "ffmpeg";

    /** ffprobe 可执行文件路径，空则从 PATH 查找 {@code ffprobe}。 */
    private String probePath = "ffprobe";

    /** 工作目录，用于存放中间产物与相对路径引用。 */
    private String workDir = "${java.io.tmpdir}/easywing-aid";

    /** 单条命令超时（毫秒），防止坏输入导致进程悬挂。 */
    private long timeoutMs = 300_000L;

    /** 默认输出分辨率，如 {@code 1280x720}。 */
    private String defaultResolution = "1280x720";

    /** 默认输出帧率。 */
    private int defaultFrameRate = 25;

    /** 默认视频码率，如 {@code 2M}。 */
    private String videoBitrate = "2M";

    /** 音频采样率。 */
    private int audioSampleRate = 44_100;

    /** 字幕字体名称（用于 force_style）。 */
    private String subtitleFont = "Microsoft YaHei";

    /** 字幕字号。 */
    private int subtitleFontSize = 22;
}
