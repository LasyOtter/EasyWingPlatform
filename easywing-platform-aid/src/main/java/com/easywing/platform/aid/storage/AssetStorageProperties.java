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

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资产参考图存储配置（{@code easywing.aid.storage.*}）。
 * <p>
 * M1 落地为本地磁盘 + 静态资源映射：实现简单、无外部依赖，足以验证「上传 → 回显 → 提交模型」链路。
 * 生产环境应替换为对象存储（OSS/MinIO），只需更换 {@link AssetStorageService} 实现，
 * 库内 {@code reference_url} 字段与接口契约保持不变。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.aid.storage")
public class AssetStorageProperties {

    /** 是否启用上传端点。默认 true；设为 false 时接口返回 503，便于纯后端联调环境关闭。 */
    private boolean enabled = true;

    /**
     * 存储根目录。
     * <p>相对路径按服务进程工作目录解析；容器部署建议挂载固定卷并通过环境变量覆盖。</p>
     */
    private String baseDir = "${java.io.tmpdir}/easywing-aid/assets";

    /** 对外访问前缀，落到库里的 URL 会带上它。 */
    private String publicPath = "/api/v1/aid/assets/files";

    /** 单文件大小上限（字节），默认 20MB——参考图不需要视频级别的上限。 */
    private long maxFileSize = 20L * 1024 * 1024;

    /** 允许的扩展名（小写，不含点）。 */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp", "gif");
}
