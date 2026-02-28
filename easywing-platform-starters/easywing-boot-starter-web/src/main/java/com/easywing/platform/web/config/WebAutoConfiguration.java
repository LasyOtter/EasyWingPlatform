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
package com.easywing.platform.web.config;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.core.exception.SystemException;
import com.easywing.platform.core.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Web 全局异常处理自动配置
 * <p>
 * 遵循 RFC 9457 Problem Details 规范
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler")
public class WebAutoConfiguration {

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }

    @RestControllerAdvice
    @RequiredArgsConstructor
    public static class GlobalExceptionHandler {

        @ExceptionHandler(BizException.class)
        public ResponseEntity<ProblemDetail> handleBizException(BizException ex, WebRequest request) {
            log.warn("Business exception: {}", ex.getMessage());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
            problem.setType(URI.create("https://easywing.io/errors/business"));
            problem.setTitle("Business Error");
            problem.setInstance(URI.create(request.getDescription(false)));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        @ExceptionHandler(SystemException.class)
        public ResponseEntity<ProblemDetail> handleSystemException(SystemException ex, WebRequest request) {
            log.error("System exception: ", ex);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "系统异常，请稍后重试");
            problem.setType(URI.create("https://easywing.io/errors/system"));
            problem.setTitle("System Error");
            problem.setInstance(URI.create(request.getDescription(false)));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        @ExceptionHandler(ValidationException.class)
        public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex, WebRequest request) {
            log.warn("Validation exception: {}", ex.getMessage());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
            problem.setType(URI.create("https://easywing.io/errors/validation"));
            problem.setTitle("Validation Error");
            problem.setInstance(URI.create(request.getDescription(false)));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
                MethodArgumentNotValidException ex, WebRequest request) {
            log.warn("Validation failed: {}", ex.getMessage());
            Map<String, String> errors = new HashMap<>();
            ex.getBindingResult().getFieldErrors()
                    .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "参数校验失败");
            problem.setType(URI.create("https://easywing.io/errors/validation"));
            problem.setTitle("Validation Error");
            problem.setProperty("errors", errors);
            problem.setInstance(URI.create(request.getDescription(false)));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
            log.error("Unhandled exception: ", ex);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
            problem.setType(URI.create("https://easywing.io/errors/internal"));
            problem.setTitle("Internal Server Error");
            problem.setInstance(URI.create(request.getDescription(false)));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
    }

    public WebAutoConfiguration() {
        log.info("EasyWing Web AutoConfiguration initialized");
    }
}
