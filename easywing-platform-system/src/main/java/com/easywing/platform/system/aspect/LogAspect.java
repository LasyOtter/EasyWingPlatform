package com.easywing.platform.system.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.entity.SysOperLog;
import com.easywing.platform.system.service.SysOperLogService;
import com.easywing.platform.system.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysOperLogService operLogService;
    private static final int MAX_PARAM_LENGTH = 2000;

    @Pointcut("@annotation(com.easywing.platform.system.annotation.Log)")
    public void logPointcut() {}

    @AfterReturning(pointcut = "logPointcut()", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Object jsonResult) {
        handleLog(joinPoint, null, jsonResult);
    }

    @AfterThrowing(value = "logPointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e, null);
    }

    @Async
    protected void handleLog(JoinPoint joinPoint, Exception e, Object jsonResult) {
        try {
            Log logAnnotation = getLogAnnotation(joinPoint);
            if (logAnnotation == null) return;
            HttpServletRequest request = getRequest();
            if (request == null) return;

            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(0);
            operLog.setOperTime(LocalDateTime.now());
            operLog.setOperIp(getClientIp(request));
            operLog.setOperUrl(StrUtil.sub(request.getRequestURI(), 0, 500));
            operLog.setOperName(SecurityUtils.getCurrentUsername());
            operLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName());
            operLog.setRequestMethod(request.getMethod());
            operLog.setTitle(logAnnotation.title());
            operLog.setBusinessType(logAnnotation.businessType().getCode());
            operLog.setOperatorType(logAnnotation.operatorType().getCode());

            if (e != null) {
                operLog.setStatus(1);
                operLog.setErrorMsg(StrUtil.sub(e.getMessage(), 0, 2000));
            }

            if (logAnnotation.isSaveResponseData() && jsonResult != null) {
                String resultStr = JSONUtil.toJsonStr(jsonResult);
                operLog.setJsonResult(StrUtil.sub(resultStr, 0, MAX_PARAM_LENGTH));
            }

            operLogService.insertOperLog(operLog);
        } catch (Exception ex) {
            log.error("记录操作日志异常", ex);
        }
    }

    private Log getLogAnnotation(JoinPoint joinPoint) {
        try {
            return joinPoint.getTarget().getClass()
                    .getMethod(joinPoint.getSignature().getName(),
                            ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes())
                    .getAnnotation(Log.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }
}
