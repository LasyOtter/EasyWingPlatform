package com.easywing.platform.ai.web;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.core.exception.SystemException;
import com.easywing.platform.core.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 服务全局异常处理器
 * <p>
 * 将异常统一转换为 RFC 9457 Problem Details 格式（application/problem+json），
 * 扩展字段与平台其他服务保持一致：errorCode、timestamp、traceId、errors。
 * <p>
 * AI 服务不依赖 easywing-web 模块，避免引入 Spring Security、Resilience4j 限流等
 * 与本服务无关的可选依赖，因此在此单独实现异常到 Problem Detail 的映射。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class AiGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiGlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ProblemDetail> handleBizException(BizException ex, HttpServletRequest request) {
        log.warn("Business exception occurred: {} - {}", ex.getErrorCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getTitle(), ex.getDetail(), ex.getErrorCode(), request, null);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ProblemDetail> handleSystemException(SystemException ex, HttpServletRequest request) {
        log.error("System exception occurred: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "System Error",
                ErrorCode.getMessage(ErrorCode.SYSTEM_ERROR), ex.getErrorCode(), request, null);
    }

    /**
     * 处理验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.warn("Validation exception: {}", ex.getMessage());
        List<ValidationError> errors = ex.getErrors().stream()
                .map(error -> new ValidationError(error.field(), error.message(), null, null))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST.value(), "Validation Error", ex.getMessage(),
                ErrorCode.VALIDATION_ERROR, request, errors);
    }

    /**
     * 处理 @Valid 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Method argument validation failed: {}", ex.getMessage());
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST.value(), "Validation Error", "请求参数验证失败",
                ErrorCode.VALIDATION_ERROR, request, errors);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(BindException ex, HttpServletRequest request) {
        log.warn("Bind exception: {}", ex.getMessage());
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST.value(), "Validation Error", "请求参数绑定失败",
                ErrorCode.VALIDATION_ERROR, request, errors);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST.value(), "Validation Error", "请求参数验证失败",
                ErrorCode.VALIDATION_ERROR, request, errors);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        List<ValidationError> errors = List.of(new ValidationError(ex.getParameterName(), "参数不能为空", null, null));
        return build(HttpStatus.BAD_REQUEST.value(), "Missing Parameter",
                String.format("缺少必要参数: %s", ex.getParameterName()),
                ErrorCode.MISSING_PARAMETER, request, errors);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, TypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleTypeMismatchException(Exception ex, HttpServletRequest request) {
        log.warn("Type mismatch: {}", ex.getMessage());
        String paramName = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : "unknown";
        return build(HttpStatus.BAD_REQUEST.value(), "Invalid Parameter Type",
                String.format("参数类型不正确: %s", paramName), ErrorCode.INVALID_FORMAT, request, null);
    }

    /**
     * 处理请求体不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST.value(), "Invalid Request Body", "请求体格式不正确",
                ErrorCode.INVALID_FORMAT, request, null);
    }

    /**
     * 处理 HTTP 方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {}", ex.getMethod());
        return build(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed",
                String.format("不支持的HTTP方法: %s", ex.getMethod()),
                ErrorCode.VALIDATION_ERROR, request, null);
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported: {}", ex.getContentType());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported Media Type",
                String.format("不支持的媒体类型: %s", ex.getContentType()),
                ErrorCode.INVALID_FORMAT, request, null);
    }

    /**
     * 处理 404 异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return build(HttpStatus.NOT_FOUND.value(), "Not Found", "请求的资源不存在",
                ErrorCode.RESOURCE_NOT_FOUND, request, null);
    }

    /**
     * 处理 Spring ErrorResponseException
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponseException(
            ErrorResponseException ex, HttpServletRequest request) {
        log.warn("Error response: {}", ex.getMessage());
        ProblemDetail body = ex.getBody();
        Object errorCode = body.getProperties().get("errorCode");
        return build(ex.getStatusCode().value(), body.getTitle(), body.getDetail(),
                errorCode instanceof String code ? code : ErrorCode.SYSTEM_ERROR, request, null);
    }

    /**
     * 处理所有未预期的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.error("Unexpected system error [traceId={}] from IP {}: {}",
                traceId, getClientIp(request), ex.getMessage(), ex);

        String detail = traceId != null
                ? "系统发生未知错误，请联系管理员 [traceId=" + traceId + "]"
                : "系统发生未知错误，请联系管理员";
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                detail, ErrorCode.SYSTEM_ERROR, request, null);
    }

    private ResponseEntity<ProblemDetail> build(int status, String title, String detail, String errorCode,
                                               HttpServletRequest request, List<ValidationError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.easywing.io/errors/" + errorCode));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now());
        String traceId = getTraceId(request);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    private ValidationError toFieldError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage(),
                fieldError.getRejectedValue(), fieldError.getCode());
    }

    private ValidationError toFieldError(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String fieldName = propertyPath.contains(".")
                ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1)
                : propertyPath;
        return new ValidationError(fieldName, violation.getMessage(), violation.getInvalidValue(),
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }

    private String getTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(HttpHeaders.X_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = MDC.get("traceId");
        }
        return traceId;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader(HttpHeaders.X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 校验错误明细
     */
    public record ValidationError(String field, String message, Object rejectedValue, String code) {
    }
}
