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
package com.easywing.platform.aid.storage;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资产参考图存储（M1 交付物：资产库页「上传参考图」）。
 * <p>
 * 本地磁盘实现：文件名统一重写为 UUID，原始文件名只保留在日志与返回值里——
 * 直接用客户端文件名拼接路径会引入路径穿越风险。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetStorageService {

    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final AssetStorageProperties properties;

    /**
     * 保存一张参考图。
     *
     * @param file 上传文件
     * @return 可对外访问的相对 URL（含 {@code publicPath} 前缀）
     */
    public String store(MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.AID_CAPABILITY_EXCEEDED, "参考图上传未启用");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "上传文件为空");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BizException(ErrorCode.AID_CAPABILITY_EXCEEDED, "文件大小超过上限");
        }

        String extension = extensionOf(file.getOriginalFilename());
        List<String> allowed = properties.getAllowedExtensions() == null
                ? List.copyOf(DEFAULT_EXTENSIONS)
                : properties.getAllowedExtensions();
        if (!allowed.contains(extension)) {
            throw new BizException(
                    ErrorCode.AID_CAPABILITY_EXCEEDED, "不支持的图片类型: " + (extension.isEmpty() ? "未知" : "." + extension));
        }

        Path dir = resolveBaseDir();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID().toString() + "." + extension);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("保存参考图: name={}, bytes={}, path={}", file.getOriginalFilename(), file.getSize(), target);
            return properties.getPublicPath() + "/" + target.getFileName();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "参考图保存失败: " + e.getMessage());
        }
    }

    /**
     * 读取已保存的文件，供静态资源端点回显。
     *
     * @param fileName 文件名（不含目录）
     * @return 文件路径
     */
    public Path resolve(String fileName) {
        String cleaned = StringUtils.cleanPath(fileName == null ? "" : fileName);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String leaf = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        if (!StringUtils.hasText(leaf) || leaf.contains("..")) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "文件不存在");
        }
        return resolveBaseDir().resolve(leaf).normalize();
    }

    private Path resolveBaseDir() {
        String raw = properties.getBaseDir();
        Path path = Paths.get(raw == null || raw.isBlank() ? "${java.io.tmpdir}/easywing-aid/assets" : raw);
        return path.isAbsolute()
                ? path
                : Paths.get(System.getProperty("user.dir")).resolve(path);
    }

    /** 取小写扩展名；无扩展名返回空串，交由调用方拒绝。 */
    private static String extensionOf(String rawName) {
        if (rawName == null) {
            return "";
        }
        String cleaned = StringUtils.cleanPath(rawName);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}
