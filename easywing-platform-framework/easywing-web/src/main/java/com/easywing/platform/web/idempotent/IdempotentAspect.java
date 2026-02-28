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
package com.easywing.platform.web.idempotent;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性保护切面
 * <p>
 * 基于Redis分布式锁实现，防止重复提交。
 * 使用Lua脚本确保原子性操作。
 * <p>
 * 特性：
 * <ul>
 *     <li>业务执行成功后保留幂等键5秒，防止极短时间内的重复请求</li>
 *     <li>业务执行失败立即释放幂等键，允许重试</li>
 *     <li>支持SPEL表达式解析幂等键</li>
 *     <li>解析失败有降级处理</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 释放锁的Lua脚本
     * 确保只删除自己设置的锁
     */
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    /**
     * 业务执行成功后保留幂等键的最短时间（秒）
     */
    private static final int MIN_RETENTION_SECONDS = 5;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = generateKey(point, idempotent.key());
        String lockValue = UUID.randomUUID().toString();

        // 尝试获取锁
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, idempotent.expire(), TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(locked)) {
            // 获取已存在的锁信息
            String existingValue = redisTemplate.opsForValue().get(key);
            log.warn("Duplicate request detected: key={}, existingLock={}, method={}.{}", 
                    key, existingValue != null ? "yes" : "no",
                    point.getTarget().getClass().getSimpleName(),
                    point.getSignature().getName());
            
            throw new BizException(ErrorCode.REQUEST_DUPLICATE, idempotent.message());
        }

        try {
            // 执行实际业务逻辑
            Object result = point.proceed();

            // 成功执行后，保留幂等键一段时间（防止极短时间内的重复请求）
            // 业务执行成功后，保留幂等键一段时间，防止客户端重复提交
            if (idempotent.expire() > MIN_RETENTION_SECONDS) {
                redisTemplate.expire(key, MIN_RETENTION_SECONDS, TimeUnit.SECONDS);
            }

            return result;
        } catch (Exception e) {
            // 业务执行失败，立即释放幂等键，允许重试
            releaseLock(key, lockValue);
            throw e;
        }
    }

    /**
     * 释放分布式锁
     *
     * @param key       锁key
     * @param lockValue 锁value（UUID）
     */
    private void releaseLock(String key, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(RELEASE_LOCK_SCRIPT);
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(key), lockValue);
        } catch (Exception e) {
            log.warn("Failed to release idempotent lock: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 生成幂等性key
     *
     * @param point    切入点
     * @param spel     SPEL表达式
     * @return         幂等性key
     */
    private String generateKey(ProceedingJoinPoint point, String spel) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        // 默认使用类名+方法名+用户ID
        if (!StringUtils.hasText(spel)) {
            String userId = getCurrentUserId();
            return String.format("idempotent:%s:%s:%s", className, methodName, userId);
        }

        // 解析SPEL表达式
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", point.getArgs());
        context.setVariable("userId", getCurrentUserId());

        // 设置方法参数作为变量
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Expression expression = parser.parseExpression(spel);
            Object value = expression.getValue(context);
            return String.format("idempotent:%s:%s:%s", className, methodName, 
                    value != null ? value.toString() : "null");
        } catch (SpelEvaluationException e) {
            log.error("Failed to parse SpEL expression: {}", spel, e);
            // 解析失败时使用默认key
            return String.format("idempotent:%s:%s:%s", className, methodName, 
                    System.currentTimeMillis());
        }
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，未登录返回"anonymous"
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            return subject != null ? subject : "anonymous";
        }
        return "anonymous";
    }
}
