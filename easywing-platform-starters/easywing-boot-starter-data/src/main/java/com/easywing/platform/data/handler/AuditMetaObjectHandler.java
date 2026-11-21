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
package com.easywing.platform.data.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.easywing.platform.data.properties.DataProperties;
import com.easywing.platform.data.tenant.TenantContext;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 审计字段自动填充处理器
 * <p>
 * 自动填充创建时间、更新时间、创建人、更新人、逻辑删除标识、租户ID等字段
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "easywing.data", name = "audit-enabled", havingValue = "true", matchIfMissing = true)
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final DataProperties dataProperties;

    public AuditMetaObjectHandler(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug(
                "Start insert fill for entity: {}",
                metaObject.getOriginalObject().getClass().getSimpleName());
        LocalDateTime now = LocalDateTime.now();

        // 填充创建时间
        this.strictInsertFill(metaObject, dataProperties.getAudit().getCreateTimeColumn(), LocalDateTime.class, now);
        // 填充更新时间
        this.strictInsertFill(metaObject, dataProperties.getAudit().getUpdateTimeColumn(), LocalDateTime.class, now);
        // 填充创建人
        this.strictInsertFill(
                metaObject, dataProperties.getAudit().getCreateByColumn(), String.class, getCurrentUserId());
        // 填充更新人
        this.strictInsertFill(
                metaObject, dataProperties.getAudit().getUpdateByColumn(), String.class, getCurrentUserId());
        // 填充逻辑删除标识（0-正常）
        this.strictInsertFill(metaObject, dataProperties.getAudit().getDeletedColumn(), Integer.class, 0);
        // 填充租户ID
        if (dataProperties.isTenantEnabled()) {
            this.strictInsertFill(
                    metaObject, dataProperties.getAudit().getTenantIdColumn(), Long.class, TenantContext.getTenantId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug(
                "Start update fill for entity: {}",
                metaObject.getOriginalObject().getClass().getSimpleName());
        // 填充更新时间
        this.strictUpdateFill(
                metaObject, dataProperties.getAudit().getUpdateTimeColumn(), LocalDateTime.class, LocalDateTime.now());
        // 填充更新人
        this.strictUpdateFill(
                metaObject, dataProperties.getAudit().getUpdateByColumn(), String.class, getCurrentUserId());
    }

    /**
     * 获取当前用户ID
     * <p>
     * 从安全上下文中获取当前用户ID
     * 支持从 Spring Security 或 HTTP Header (X-User-Id) 获取
     *
     * @return 当前用户ID，未认证时返回 "system"
     */
    protected String getCurrentUserId() {
        try {
            // 方式1: 尝试从 Spring Security 上下文获取
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication
                            instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                // 优先从 Principal 获取用户ID
                Object principal = authentication.getPrincipal();
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    return userDetails.getUsername();
                } else if (principal instanceof String) {
                    return (String) principal;
                }
            }

            // 方式2: 从 RequestContextHolder 获取（网关传递的 Header）
            org.springframework.web.context.request.RequestAttributes requestAttributes =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();

            if (requestAttributes
                    instanceof
                    org.springframework.web.context.request.ServletRequestAttributes
                    servletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = servletRequestAttributes.getRequest();
                String userId = request.getHeader("X-User-Id");
                if (userId != null && !userId.isEmpty()) {
                    return userId;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get current user id from context: {}", e.getMessage());
        }

        // 默认返回系统用户
        return "system";
    }
}
