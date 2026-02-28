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
package com.easywing.platform.web.ratelimit;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 限流保护切面
 * <p>
 * 基于Redis令牌桶算法实现分布式限流。
 * 使用Lua脚本确保原子性操作。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    public RateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /**
     * 令牌桶限流Lua脚本
     * <p>
     * 算法说明：
     * 1. 计算从上次更新到现在的时间差，补充相应数量的令牌
     * 2. 如果令牌数 >= 请求数，允许请求并扣除令牌
     * 3. 如果令牌数 < 请求数，拒绝请求
     * 4. 更新令牌数和上次更新时间
     */
    private static final String TOKEN_BUCKET_SCRIPT =
            "local key = KEYS[1] " +
                    "local rate = tonumber(ARGV[1]) " +
                    "local capacity = tonumber(ARGV[2]) " +
                    "local now = tonumber(ARGV[3]) " +
                    "local requested = tonumber(ARGV[4]) " +

                    "local fill_time = capacity / rate " +
                    "local ttl = math.floor(fill_time * 2) " +

                    "local last_updated = redis.call('get', key .. ':last_updated') " +
                    "local tokens = redis.call('get', key .. ':tokens') " +

                    "if last_updated == false then " +
                    "    tokens = capacity " +
                    "else " +
                    "    local delta = math.max(0, now - tonumber(last_updated)) " +
                    "    tokens = math.min(capacity, tonumber(tokens) + (delta * rate)) " +
                    "end " +

                    "if tokens >= requested then " +
                    "    tokens = tokens - requested " +
                    "    redis.call('setex', key .. ':tokens', ttl, tokens) " +
                    "    redis.call('setex', key .. ':last_updated', ttl, now) " +
                    "    return 1 " +
                    "else " +
                    "    redis.call('setex', key .. ':tokens', ttl, tokens) " +
                    "    redis.call('setex', key .. ':last_updated', ttl, now) " +
                    "    return 0 " +
                    "end";

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = "rate_limit:" + generateKey(point, rateLimit.key());

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(TOKEN_BUCKET_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(rateLimit.rate()),
                String.valueOf(rateLimit.capacity()),
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"
        );

        if (result == null || result == 0) {
            log.warn("Rate limit exceeded: key={}, rate={}, capacity={}", key, rateLimit.rate(), rateLimit.capacity());
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimit.message());
        }

        return point.proceed();
    }

    /**
     * 生成限流key
     *
     * @param point 切入点
     * @param spel  SPEL表达式
     * @return      限流key
     */
    private String generateKey(ProceedingJoinPoint point, String spel) {
        // 如果指定了SPEL表达式，解析它
        if (StringUtils.hasText(spel)) {
            return parseSpel(point, spel);
        }

        // 默认使用客户端IP
        return getClientIp();
    }

    /**
     * 解析SPEL表达式
     *
     * @param point 切入点
     * @param spel  SPEL表达式
     * @return      解析结果
     */
    private String parseSpel(ProceedingJoinPoint point, String spel) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 设置方法参数
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Object value = parser.parseExpression(spel).getValue(context);
        return value != null ? value.toString() : "null";
    }

    /**
     * 获取客户端IP地址
     *
     * @return 客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");

        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多个代理情况，取第一个IP
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
