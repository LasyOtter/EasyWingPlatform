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
package com.easywing.platform.auth.exception;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.web.problem.Rfc9457ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证服务全局异常处理器
 * <p>
 * 认证服务直接依赖 {@code easywing-web}（未引入 starter-web），
 * 平台级异常由 {@code easywing-web} 的
 * {@link com.easywing.platform.web.exception.GlobalExceptionHandler} 统一处理；
 * 本处理器只负责认证服务特有的输入校验异常，输出与平台保持一致
 * （RFC 9457，含 errorCode / timestamp / traceId / errors 扩展字段）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    /**
     * 处理 @Valid 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Rfc9457ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Rfc9457ProblemDetail.ValidationError(
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue(), error.getCode()))
                .toList();

        Rfc9457ProblemDetail problem = Rfc9457ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST)
                .title("Validation Error")
                .detail("请求参数验证失败")
                .errorCode(ErrorCode.VALIDATION_ERROR)
                .traceId(getTraceId(request))
                .instance(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(HttpHeaders.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }

    private String getTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(HttpHeaders.X_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = MDC.get("traceId");
        }
        return traceId;
    }
}
