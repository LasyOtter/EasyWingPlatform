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
package com.easywing.platform.web.problem;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * RFC 9457 Problem Detail 扩展实现
 * <p>
 * 实现RFC 9457 (Problem Details for HTTP APIs) 规范，提供标准化的错误响应格式。
 * <p>
 * 标准字段：
 * <ul>
 *     <li>type (URI) - 标识问题类型的URI引用</li>
 *     <li>title (string) - 问题类型的简短可读标题</li>
 *     <li>status (number) - HTTP状态码</li>
 *     <li>detail (string) - 此问题发生的具体详情</li>
 *     <li>instance (URI) - 标识具体问题发生的URI引用</li>
 * </ul>
 * <p>
 * 扩展字段：
 * <ul>
 *     <li>errorCode (string) - 应用定义的错误码</li>
 *     <li>timestamp (string) - 错误发生时间 (ISO 8601)</li>
 *     <li>traceId (string) - 分布式追踪ID</li>
 *     <li>errors (array) - 验证错误列表</li>
 * </ul>
 *
 * @author EasyWing Team
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rfc9457ProblemDetail {

    /**
     * 问题类型URI基础路径
     */
    private static final String TYPE_BASE_URI = "https://api.easywing.io/errors/";

    /**
     * Spring ProblemDetail
     */
    @JsonIgnore
    private final ProblemDetail problemDetail;

    /**
     * 应用错误码
     */
    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * 时间戳
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * 追踪ID
     */
    @JsonProperty("traceId")
    private String traceId;

    /**
     * 验证错误列表
     */
    @JsonProperty("errors")
    private List<ValidationError> errors;

    /**
     * 扩展属性
     */
    @JsonIgnore
    private final Map<String, Object> properties = new LinkedHashMap<>();

    private Rfc9457ProblemDetail(Builder builder) {
        this.problemDetail = ProblemDetail.forStatusAndDetail(builder.status, builder.detail);
        this.problemDetail.setTitle(builder.title);
        this.problemDetail.setType(builder.type != null ? builder.type : URI.create(TYPE_BASE_URI + builder.errorCode));
        if (builder.instance != null) {
            this.problemDetail.setInstance(builder.instance);
        }
        this.errorCode = builder.errorCode;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.traceId = builder.traceId;
        this.errors = builder.errors;
        this.properties.putAll(builder.properties);
    }

    public URI getType() {
        return problemDetail.getType();
    }

    public String getTitle() {
        return problemDetail.getTitle();
    }

    public int getStatus() {
        return problemDetail.getStatus();
    }

    public String getDetail() {
        return problemDetail.getDetail();
    }

    public URI getInstance() {
        return problemDetail.getInstance();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * 从异常创建ProblemDetail
     */
    public static Rfc9457ProblemDetail fromException(Throwable ex, HttpStatus status) {
        return builder()
                .status(status)
                .title(status.getReasonPhrase())
                .detail(ex.getMessage())
                .build();
    }

    /**
     * 从ErrorResponse创建ProblemDetail
     */
    public static Rfc9457ProblemDetail fromErrorResponse(ErrorResponse errorResponse) {
        return builder()
                .status(HttpStatus.valueOf(errorResponse.getStatusCode().value()))
                .title(errorResponse.getBody().getTitle())
                .detail(errorResponse.getBody().getDetail())
                .type(errorResponse.getBody().getType())
                .instance(errorResponse.getBody().getInstance())
                .build();
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 验证错误
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidationError(
            @JsonProperty("field") String field,
            @JsonProperty("message") String message,
            @JsonProperty("rejectedValue") Object rejectedValue,
            @JsonProperty("code") String code
    ) {
        public ValidationError(String field, String message) {
            this(field, message, null, null);
        }
    }

    /**
     * 构建器
     */
    public static class Builder {
        private HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        private String title = "Error";
        private String detail = "An error occurred";
        private URI type;
        private URI instance;
        private String errorCode;
        private Instant timestamp;
        private String traceId;
        private List<ValidationError> errors;
        private final Map<String, Object> properties = new LinkedHashMap<>();

        public Builder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public Builder status(int statusCode) {
            this.status = HttpStatus.valueOf(statusCode);
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder type(URI type) {
            this.type = type;
            return this;
        }

        public Builder type(String type) {
            this.type = URI.create(type);
            return this;
        }

        public Builder instance(URI instance) {
            this.instance = instance;
            return this;
        }

        public Builder instance(String instance) {
            this.instance = URI.create(instance);
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder errors(List<ValidationError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder error(ValidationError error) {
            if (this.errors == null) {
                this.errors = new ArrayList<>();
            }
            this.errors.add(error);
            return this;
        }

        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public Rfc9457ProblemDetail build() {
            return new Rfc9457ProblemDetail(this);
        }
    }
}
