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
package com.easywing.platform.web.exception;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.core.exception.SystemException;
import com.easywing.platform.core.exception.ValidationException;
import com.easywing.platform.web.problem.Rfc9457ProblemDetail;
import com.easywing.platform.web.problem.Rfc9457ProblemDetail.ValidationError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 将所有异常统一转换为RFC 9457 Problem Details格式响应
 * <p>
 * Content-Type: application/problem+json
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleBizException(BizException ex, HttpServletRequest request) {
        log.warn("Business exception occurred: {} - {}", ex.getErrorCode(), ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(ex.getStatus())
                .title(ex.getTitle())
                .detail(ex.getDetail())
                .errorCode(ex.getErrorCode())
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.valueOf(ex.getStatus()))
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleSystemException(SystemException ex, HttpServletRequest request) {
        log.error("System exception occurred: {}", ex.getMessage(), ex);

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .title("System Error")
                .detail("系统内部错误，请稍后重试")
                .errorCode(ex.getErrorCode())
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.warn("Validation exception: {}", ex.getMessage());

        List<ValidationError> errors = ex.getErrors().stream()
                .map(e -> new ValidationError(e.field(), e.message()))
                .collect(Collectors.toList());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Validation Error")
                .detail(ex.getMessage())
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理Spring Validation参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Method argument validation failed: {}", ex.getMessage());

        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .collect(Collectors.toList());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Validation Error")
                .detail("请求参数验证失败")
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleBindException(BindException ex, HttpServletRequest request) {
        log.warn("Bind exception: {}", ex.getMessage());

        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .collect(Collectors.toList());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Validation Error")
                .detail("请求参数绑定失败")
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(this::toValidationError)
                .collect(Collectors.toList());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Validation Error")
                .detail("请求参数验证失败")
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getParameterName());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Missing Parameter")
                .detail(String.format("缺少必要参数: %s", ex.getParameterName()))
                .errorCode(ErrorCode.MISSING_PARAMETER)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .error(new ValidationError(ex.getParameterName(), "参数不能为空"))
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, TypeMismatchException.class})
    public ResponseEntity<Rfc9457ProblemDetail> handleTypeMismatchException(
            Exception ex, HttpServletRequest request) {
        log.warn("Type mismatch: {}", ex.getMessage());

        String paramName = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : "unknown";

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Invalid Parameter Type")
                .detail(String.format("参数类型不正确: %s", paramName))
                .errorCode(ErrorCode.INVALID_FORMAT)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理HTTP消息不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Invalid Request Body")
                .detail("请求体格式不正确")
                .errorCode(ErrorCode.INVALID_FORMAT)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理HTTP方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {}", ex.getMethod());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .title("Method Not Allowed")
                .detail(String.format("不支持的HTTP方法: %s", ex.getMethod()))
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported: {}", ex.getContentType());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .title("Unsupported Media Type")
                .detail(String.format("不支持的媒体类型: %s", ex.getContentType()))
                .errorCode(ErrorCode.INVALID_FORMAT)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.NOT_FOUND)
                .title("Not Found")
                .detail("请求的资源不存在")
                .errorCode(ErrorCode.RESOURCE_NOT_FOUND)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String username = extractUsername(ex);

        if (ex instanceof BadCredentialsException) {
            log.warn("Authentication failed from IP {}: username={}, reason={}", clientIp, username, ex.getMessage());
        } else if (ex instanceof AuthenticationCredentialsNotFoundException) {
            log.warn("Authentication token missing from IP {}: {}", clientIp, ex.getMessage());
        } else {
            log.warn("Authentication failed from IP {}: {}", clientIp, ex.getMessage());
        }

        String errorCode = ErrorCode.UNAUTHORIZED;
        String detail = "认证失败";

        if (ex instanceof AuthenticationCredentialsNotFoundException) {
            errorCode = ErrorCode.TOKEN_MISSING;
            detail = "缺少认证令牌";
        } else if (ex instanceof BadCredentialsException) {
            errorCode = ErrorCode.INVALID_CREDENTIALS;
            detail = "用户名或密码错误";
        }

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .title("Unauthorized")
                .detail(detail)
                .errorCode(errorCode)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    /**
     * 处理访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        String username = getCurrentUsername();
        String requestUri = request.getRequestURI();
        String httpMethod = request.getMethod();
        String clientIp = getClientIp(request);

        log.warn("Access denied for user {} to resource {} {} from IP {}: {}",
                username, httpMethod, requestUri, clientIp, ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.FORBIDDEN)
                .title("Access Denied")
                .detail("您没有权限访问该资源")
                .errorCode(ErrorCode.ACCESS_DENIED)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理Resilience4j限流异常
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleRequestNotPermitted(
            RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limited: {}", ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .title("Rate Limited")
                .detail("请求过于频繁，请稍后重试")
                .errorCode(ErrorCode.RATE_LIMITED)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body(problem);
    }

    /**
     * 处理Resilience4j熔断异常
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleCallNotPermittedException(
            CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("Circuit breaker open: {}", ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .title("Service Unavailable")
                .detail("服务暂时不可用，请稍后重试")
                .errorCode(ErrorCode.CIRCUIT_BREAKER_OPEN)
                .traceId(getTraceId())
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(problem);
    }

    /**
     * 处理Spring ErrorResponseException
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleErrorResponseException(
            ErrorResponseException ex, HttpServletRequest request) {
        log.warn("Error response: {}", ex.getMessage());

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.fromErrorResponse(ex);
        problem.traceId = getTraceId();

        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.error("Unexpected system error [traceId={}] from IP {}: ", traceId, getClientIp(request), ex);

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .title("Internal Server Error")
                .detail("系统发生未知错误，请联系管理员 [traceId=" + traceId + "]")
                .errorCode(ErrorCode.SYSTEM_ERROR)
                .traceId(traceId)
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue(),
                fieldError.getCode()
        );
    }

    private ValidationError toValidationError(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String fieldName = propertyPath.contains(".")
                ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1)
                : propertyPath;
        return new ValidationError(
                fieldName,
                violation.getMessage(),
                violation.getInvalidValue(),
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
        );
    }

    private String getTraceId() {
        return MDC.get("traceId");
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取当前用户名
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return "anonymous";
    }

    /**
     * 从认证异常中提取用户名
     */
    private String extractUsername(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException && ex.getMessage() != null) {
            return ex.getMessage().contains(":") ? ex.getMessage().split(":")[0] : "unknown";
        }
        return "unknown";
    }
}
