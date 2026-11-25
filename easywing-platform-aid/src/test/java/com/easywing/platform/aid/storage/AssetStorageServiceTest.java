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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.core.exception.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 参考图存储测试：重点是文件名与扩展名校验，避免路径穿越与任意文件写入。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
class AssetStorageServiceTest {

    @TempDir
    Path tempDir;

    private AssetStorageProperties properties;
    private AssetStorageService service;

    @BeforeEach
    void setUp() {
        properties = new AssetStorageProperties();
        properties.setBaseDir(tempDir.toString());
        properties.setPublicPath("/api/v1/aid/assets/files");
        service = new AssetStorageService(properties);
    }

    @Test
    void storeRewritesFileNameAndReturnsPublicUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "我的参考图.PNG", "image/png", new byte[] {1, 2, 3});

        String url = service.store(file);

        assertThat(url).startsWith("/api/v1/aid/assets/files/");
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        // 客户端文件名不得直接进入存储路径（防穿越），且扩展名被归一为小写
        assertThat(fileName).doesNotContain("我的参考图").endsWith(".png");
        assertThat(service.resolve(fileName)).exists();
    }

    /** 拒绝非图片类型：参考图要直接进模型，不能变成任意文件落盘。 */
    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.jsp", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的图片类型");
    }

    /** 无扩展名同样拒绝，避免绕过白名单。 */
    @Test
    void rejectsMissingExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "noext", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上传文件为空");
    }

    @Test
    void rejectsOversizedFile() {
        properties.setMaxFileSize(4);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[] {1, 2, 3, 4, 5});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件大小超过上限");
    }

    @Test
    void disabledStorageRejectsUpload() {
        properties.setEnabled(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(BizException.class);
    }

    /**
     * 路径穿越必须被拦住。
     * <p>实现会剥离目录部分再解析到存储根下，因此 {@code ../../etc/passwd} 会被归一到
     * 「存储根/etc/passwd」，既不逃逸也不能读到外部文件。</p>
     */
    /**
     * 路径穿越必须被归一到存储根内。
     * <p>断言用 {@code toString()} 前缀比较而非 {@code startsWith(Path)}——后者会做 realpath
     * 解析，而这里要验证的正是「解析不到外部文件」，文件本身不该存在。</p>
     */
    @Test
    void resolveNormalizesTraversalIntoBaseDir() {
        Path resolved = service.resolve("../../etc/passwd");

        String base = tempDir.toAbsolutePath().normalize().toString();
        assertThat(resolved.toAbsolutePath().normalize().toString()).startsWith(base);
        // 归一后应为 <root>/etc/passwd，既不是外部路径也不含 ..
        assertThat(resolved.toString()).doesNotContain("..");
    }

    /** 带目录分隔符的合法文件名应取最后一段，等价于直接传文件名。 */
    @Test
    void resolveTakesLastPathSegment() {
        assertThat(service.resolve("sub/dir/abc.png")).isEqualTo(service.resolve("abc.png"));
    }

    /** 真实路径解析后可读，供回显端点使用。 */
    @Test
    void resolveReturnsStoredFile() throws IOException {
        Files.writeString(tempDir.resolve("abc.png"), "x");

        Path resolved = service.resolve("abc.png");

        assertThat(resolved).exists();
    }
}
