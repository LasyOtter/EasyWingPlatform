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

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * FFmpeg 视频合成服务（PRD FR-7 成片导出 / FR-5.2 尾帧接力）。
 *
 * <p><b>M0 预研结论</b>（已在本机 FFmpeg N-115858 实测通过）：</p>
 * <ul>
 *   <li>同编码参数片段用 {@code concat} demuxer + stream copy，几乎零成本；
 *       混合参数必须强制重编码，否则时间基不一致会导致音画错位。</li>
 *   <li>多段配音用 {@code adelay} 按分镜偏移精确对位后 {@code amix}，实测对齐准确。</li>
 *   <li>字幕用 {@code subtitles} 过滤器烧录；<b>必须在 workDir 下用相对文件名</b>，
 *       因为该过滤器以 {@code :} 作选项分隔符，Windows 盘符会被误解析为 image size。</li>
 *   <li>首尾帧抽取用 {@code -sseof -0.1} 取末帧，实测输出正确。</li>
 * </ul>
 *
 * <p><b>安全约定</b>：所有命令以参数数组形式交给 {@link ProcessBuilder}，不经过 shell，
 * 避免路径与字幕文本被注入解释。</p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FfmpegService {

    private final FfmpegProperties properties;

    public FfmpegService(FfmpegProperties properties) {
        this.properties = properties;
    }

    /**
     * 拼接分镜片段为完整时间轴，并叠加各片段的配音与字幕。
     *
     * @param segments      时间轴片段（顺序即拼接顺序）
     * @param outputPath    输出文件绝对路径
     * @param requestAudioMix 是否混流配音轨
     * @return 实际输出时长（秒）
     */
    public double composeTimeline(List<TimelineSegment> segments, Path outputPath, boolean requestAudioMix) {
        requireEnabled();
        if (segments == null || segments.isEmpty()) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "时间轴为空，无可合成的分镜片段");
        }
        Path workDir = prepareWorkDir();

        // 1) 拼接视频时间轴
        Path merged = workDir.resolve("merged-" + System.currentTimeMillis() + ".mp4");
        concatClips(segments, merged);

        // 2) 配音混流
        Path voiced = merged;
        if (requestAudioMix && segments.stream().anyMatch(s -> StringUtils.hasText(s.getVoicePath()))) {
            voiced = workDir.resolve("voiced-" + System.currentTimeMillis() + ".mp4");
            mixVoices(merged, segments, voiced);
        }

        // 3) 字幕烧录
        Path captioned = voiced;
        List<TimelineSegment.SubtitleEntry> subtitles = TimelineSegment.toSubtitles(segments);
        if (!subtitles.isEmpty()) {
            captioned = workDir.resolve("captioned-" + System.currentTimeMillis() + ".mp4");
            burnSubtitles(voiced, subtitles, captioned, workDir);
        }

        // 4) 统一转码到目标分辨率并落到最终位置
        encodeTo(captioned, outputPath);

        double duration = probeDuration(outputPath);
        cleanup(merged, voiced, captioned);
        log.info("成片合成完成: shots={}, duration={}s, output={}", segments.size(), duration, outputPath);
        return duration;
    }

    /**
     * 抽取视频末帧，用于「尾帧接力」——作为下一集/镜的首帧条件输入（FR-5.2）。
     *
     * @param videoPath 源视频路径
     * @param outputPng 输出 PNG 路径
     * @param seekFromEndSeconds 距结尾的秒数，通常 0.1
     * @return 输出文件路径
     */
    public Path extractLastFrame(Path videoPath, Path outputPng, double seekFromEndSeconds) {
        requireEnabled();
        requireFile(videoPath, "源视频不存在");
        List<String> cmd = new ArrayList<>(List.of(
                properties.getBinaryPath(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-sseof",
                "-" + Math.max(0.01, seekFromEndSeconds),
                "-i",
                videoPath.toString(),
                "-frames:v",
                "1",
                "-vf",
                "scale=" + properties.getDefaultResolution(),
                outputPng.toString()));
        exec(cmd, null);
        if (!Files.exists(outputPng)) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "尾帧抽取未产出文件: " + videoPath.getFileName());
        }
        return outputPng;
    }

    /**
     * 探测媒体时长（秒）。
     *
     * @param mediaPath 媒体文件路径
     * @return 时长秒数；探测失败返回 -1
     */
    public double probeDuration(Path mediaPath) {
        if (!StringUtils.hasText(properties.getProbePath())) {
            return -1;
        }
        try {
            List<String> cmd = List.of(
                    properties.getProbePath(),
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    mediaPath.toString());
            String out = execCapture(cmd);
            return Double.parseDouble(out.trim());
        } catch (Exception e) {
            log.warn("时长探测失败: {} ({})", mediaPath, e.getMessage());
            return -1;
        }
    }

    // ==================== 内部实现 ====================

    /** 同参数片段走 stream copy，否则强制重编码以保证时间基一致。 */
    private void concatClips(List<TimelineSegment> segments, Path output) {
        Path workDir = output.getParent();
        Path listFile = workDir.resolve("concat-" + System.currentTimeMillis() + ".txt");
        List<String> lines = segments.stream()
                .peek(this::requireVideo)
                .map(s -> "file '" + Path.of(s.getVideoPath()).toAbsolutePath() + "'")
                .collect(Collectors.toList());
        try {
            Files.write(listFile, lines);
        } catch (IOException e) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "写入 concat 清单失败");
        }

        // 先尝试快速路径；失败则退化为重编码路径
        List<String> fast = new ArrayList<>(List.of(
                properties.getBinaryPath(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                listFile.getFileName().toString(),
                "-c",
                "copy",
                output.getFileName().toString()));
        if (exec(fast, workDir.toFile()) != 0) {
            log.debug("stream copy 不可用，改用重编码拼接");
            List<String> robust = new ArrayList<>(List.of(
                    properties.getBinaryPath(),
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "concat",
                    "-safe",
                    "0",
                    "-i",
                    listFile.getFileName().toString(),
                    "-r",
                    String.valueOf(properties.getDefaultFrameRate()),
                    "-c:v",
                    "libx264",
                    "-preset",
                    "veryfast",
                    "-pix_fmt",
                    "yuv420p",
                    "-c:a",
                    "aac",
                    "-ar",
                    String.valueOf(properties.getAudioSampleRate()),
                    "-ac",
                    "1",
                    "-b:v",
                    properties.getVideoBitrate(),
                    output.getFileName().toString()));
            exec(robust, workDir.toFile());
        }
        if (!Files.exists(output)) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "时间轴拼接失败");
        }
    }

    /** 每段配音按 offset adelay 对位，再 amix 合并。 */
    private void mixVoices(Path video, List<TimelineSegment> segments, Path output) {
        File dir = video.getParent().toFile();
        long cursorMs = 0;
        List<String> cmd =
                new ArrayList<>(List.of(properties.getBinaryPath(), "-hide_banner", "-loglevel", "error", "-y"));
        cmd.add("-i");
        cmd.add(video.getFileName().toString());

        StringBuilder filter = new StringBuilder();
        int inputIndex = 1;
        for (TimelineSegment segment : segments) {
            if (!StringUtils.hasText(segment.getVoicePath())) {
                cursorMs += (long) (segment.getDurationSeconds() * 1000);
                continue;
            }
            cmd.add("-i");
            cmd.add(Path.of(segment.getVoicePath()).toAbsolutePath().toString());
            long delay = cursorMs + Math.max(0, segment.getVoiceDelayMs());
            filter.append('[')
                    .append(inputIndex++)
                    .append(":a]adelay=")
                    .append(delay)
                    .append('|')
                    .append(delay)
                    .append("[v")
                    .append(inputIndex)
                    .append(']');
            cursorMs += (long) (segment.getDurationSeconds() * 1000);
        }

        if (inputIndex == 1) {
            return;
        }

        filter.append(";[v1]");
        for (int i = 2; i <= inputIndex; i++) {
            filter.append("[v").append(i).append(']');
        }
        filter.append("amix=inputs=").append(inputIndex).append(":duration=first[aout]");

        cmd.addAll(List.of(
                "-filter_complex",
                filter.toString(),
                "-map",
                "0:v",
                "-map",
                "[aout]",
                "-c:v",
                "copy",
                "-c:a",
                "aac",
                "-ar",
                String.valueOf(properties.getAudioSampleRate()),
                output.getFileName().toString()));
        exec(cmd, dir);
        if (!Files.exists(output)) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "配音混流失败");
        }
    }

    /** 在 workDir 内用相对文件名烧录 SRT 字幕。 */
    private void burnSubtitles(Path video, List<TimelineSegment.SubtitleEntry> subtitles, Path output, Path workDir) {
        String srtName = "subs-" + System.currentTimeMillis() + ".srt";
        Path srt = workDir.resolve(srtName);
        try {
            Files.writeString(
                    srt,
                    subtitles.stream()
                            .map(TimelineSegment.SubtitleEntry::toSrtBlock)
                            .collect(Collectors.joining("\n")));
        } catch (IOException e) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "写入字幕文件失败");
        }

        String forceStyle =
                "FontName=" + properties.getSubtitleFont() + ",FontSize=" + properties.getSubtitleFontSize();
        List<String> cmd = new ArrayList<>(List.of(
                properties.getBinaryPath(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                video.getFileName().toString(),
                "-vf",
                "subtitles=" + srtName + ":force_style='" + forceStyle + "'",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "copy",
                output.getFileName().toString()));
        exec(cmd, workDir.toFile());
        if (!Files.exists(output)) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "字幕烧录失败");
        }
    }

    /** 统一转码到目标分辨率/码率。 */
    private void encodeTo(Path source, Path target) {
        File dir = source.getParent().toFile();
        List<String> cmd = new ArrayList<>(List.of(
                properties.getBinaryPath(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                source.getFileName().toString(),
                "-vf",
                "scale=" + properties.getDefaultResolution(),
                "-r",
                String.valueOf(properties.getDefaultFrameRate()),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-pix_fmt",
                "yuv420p",
                "-b:v",
                properties.getVideoBitrate(),
                "-c:a",
                "aac",
                "-ar",
                String.valueOf(properties.getAudioSampleRate()),
                "-ac",
                "1",
                target.toString()));
        exec(cmd, dir);
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "成片转码失败");
        }
    }

    private Path prepareWorkDir() {
        Path dir = Path.of(properties.getWorkDir());
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "创建 FFmpeg 工作目录失败: " + dir);
        }
    }

    private void requireVideo(TimelineSegment segment) {
        if (!StringUtils.hasText(segment.getVideoPath())) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "分镜缺少视频产物: shotId=" + segment.getShotId());
        }
        requireFile(Path.of(segment.getVideoPath()), "分镜视频不存在: " + segment.getVideoPath());
    }

    private static void requireFile(Path path, String message) {
        if (!Files.exists(path)) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, message);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "FFmpeg 合成能力未启用");
        }
    }

    private void cleanup(Path... paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.debug("清理中间产物失败: {}", path);
            }
        }
    }

    /**
     * 执行命令并返回退出码；非 0 抛业务异常并附带 FFmpeg 的错误输出。
     */
    private int exec(List<String> command, File workDir) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workDir != null) {
                builder.directory(workDir);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            if (!process.waitFor(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "FFmpeg 执行超时（" + properties.getTimeoutMs() + "ms）");
            }
            if (process.exitValue() != 0) {
                log.warn("FFmpeg 非零退出: exit={}, output={}", process.exitValue(), truncate(output));
                throw new BizException(
                        ErrorCode.AID_FFMPEG_FAILED,
                        "FFmpeg 执行失败（exit=" + process.exitValue() + "）: " + firstErrorLine(output));
            }
            log.debug("FFmpeg 执行成功: {}ms, cmd={}", System.currentTimeMillis() - start, summary(command));
            return 0;
        } catch (BizException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "FFmpeg 进程启动失败: " + e.getMessage());
        }
    }

    /** 执行命令并返回标准输出。 */
    private String execCapture(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "ffprobe 执行超时");
        }
        if (process.exitValue() != 0) {
            throw new BizException(ErrorCode.AID_FFMPEG_FAILED, "ffprobe 执行失败: " + truncate(output));
        }
        return output;
    }

    private static String firstErrorLine(String output) {
        if (output == null || output.isBlank()) {
            return "无错误输出";
        }
        return output.lines()
                .filter(line -> line.contains("Error") || line.contains("Invalid") || line.contains("No such"))
                .findFirst()
                .orElse(truncate(output));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    private static String summary(List<String> command) {
        String joined = String.join(" ", command);
        return joined.length() > 200 ? joined.substring(0, 200) + "..." : joined;
    }
}
