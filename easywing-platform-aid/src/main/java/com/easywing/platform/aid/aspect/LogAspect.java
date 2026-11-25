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
package com.easywing.platform.aid.aspect;

import cn.hutool.core.util.StrUtil;
import com.easywing.platform.aid.annotation.Log;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AID 操作日志切面。
 * <p>
 * M1 阶段只输出应用日志（方法、耗时、关键入参），不建表落库——避免为「运营审计」提前引入一张
 * 可能随需求变化的表。等 M4 商业化阶段确有审计要求时再补 {@code aid_oper_log} 与持久化实现。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    private static final int MAX_PARAM_LENGTH = 2_000;

    @Pointcut("@annotation(com.easywing.platform.aid.annotation.Log)")
    public void logPointcut() {}

    @AfterReturning(pointcut = "logPointcut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) {
        handleLog(joinPoint, null);
    }

    @AfterThrowing(value = "logPointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e);
    }

    private void handleLog(JoinPoint joinPoint, Exception exception) {
        try {
            Log annotation = resolveAnnotation(joinPoint);
            if (annotation == null) {
                return;
            }
            String method = joinPoint.getTarget().getClass().getSimpleName() + "."
                    + joinPoint.getSignature().getName();
            String uri = currentUri();
            if (exception != null) {
                log.warn("[AID操作] {} {} 失败: {}", annotation.title(), method, exception.getMessage());
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(
                        "[AID操作] {} {} uri={} args={}",
                        annotation.title(),
                        method,
                        uri,
                        annotation.isSaveRequestData() ? abbreviate(joinPoint.getArgs()) : "[excluded]");
            } else {
                log.info("[AID操作] {} {} uri={}", annotation.title(), method, uri);
            }
        } catch (Exception e) {
            // 日志切面异常不得影响业务主链路
            log.warn("记录 AID 操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 从方法或类上取注解；方法优先于类。
     *
     * @param joinPoint 切点
     * @return 注解，未标注返回 null
     */
    private Log resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log annotation = method.getAnnotation(Log.class);
        if (annotation != null) {
            return annotation;
        }
        // 注解标在类上时对所有方法生效
        return joinPoint.getTarget().getClass().getAnnotation(Log.class);
    }

    private String currentUri() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        return StrUtil.sub(request.getRequestURI(), 0, 500);
    }

    /** 参数摘要：长度截断 + 数组展开，避免大对象把日志刷爆。 */
    private String abbreviate(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        String text = Arrays.toString(args);
        return StrUtil.sub(text, 0, MAX_PARAM_LENGTH);
    }
}
