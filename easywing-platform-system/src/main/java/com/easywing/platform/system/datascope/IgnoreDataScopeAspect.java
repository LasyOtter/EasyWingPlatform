package com.easywing.platform.system.datascope;

import com.easywing.platform.data.annotation.IgnoreDataScope;
import com.easywing.platform.data.datascope.DataScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 忽略数据权限注解切面
 * <p>
 * 处理@IgnoreDataScope注解，在方法执行期间临时忽略数据权限过滤
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(-1) // 确保在事务注解之前执行
public class IgnoreDataScopeAspect {

    /**
     * 环绕通知：处理@IgnoreDataScope注解
     *
     * @param joinPoint 连接点
     * @param ignoreDataScope 忽略数据权限注解
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(ignoreDataScope)")
    public Object around(ProceedingJoinPoint joinPoint, IgnoreDataScope ignoreDataScope) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        log.debug("Ignoring data scope for method: {}, reason: {}", methodName, ignoreDataScope.reason());

        // 设置忽略数据权限标志
        DataScopeContext.setIgnoreDataScope(true);

        try {
            return joinPoint.proceed();
        } finally {
            // 清除忽略标志
            DataScopeContext.clear();
            log.debug("Data scope filter restored for method: {}", methodName);
        }
    }
}
