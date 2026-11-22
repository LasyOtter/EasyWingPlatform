package com.easywing.platform.ai.session;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话共享链接配置（PRD 4.7 / 5.2）。
 * <p>
 * 绑定 {@code easywing.ai.share.*}。共享会把会话内容暴露给令牌持有者，默认关闭，
 * 需显式开启；限流与有效期、访问次数上限均可配置。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "easywing.ai.share")
public class ShareLinkProperties {

    /** 是否开启共享链接能力，默认关闭。 */
    private boolean enabled = false;

    /** 存储后端：memory（默认）或 redis（多实例共享）。 */
    private String storageType = "memory";

    /** 新建共享链接的默认有效期（小时）；&lt;=0 表示永不过期。 */
    private int defaultTtlHours = 168;

    /** 允许设置的最大有效期（小时），防止超长链接。 */
    private int maxTtlHours = 720;

    /** 单个会话允许同时存在的共享链接上限。 */
    private int maxLinksPerSession = 5;

    /** 每个共享链接每分钟的访问次数上限（限流）。 */
    private int rateLimitPerMinute = 30;

    /** 共享内容单次返回的消息条数上限。 */
    private int maxMessages = 500;
}
