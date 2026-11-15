package com.easywing.platform.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.core.exception.SystemException;
import com.easywing.platform.core.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class AiGlobalExceptionHandlerTest {

    private AiGlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new AiGlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/ai/chat/completions");
    }

    @Test
    void handleBizExceptionUsesExceptionStatusAndErrorCode() {
        BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND);

        ResponseEntity<ProblemDetail> response = handler.handleBizException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void handleSystemExceptionReturnsInternalServerError() {
        SystemException ex = new SystemException("boom");

        ResponseEntity<ProblemDetail> response = handler.handleSystemException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", ErrorCode.SYSTEM_ERROR);
    }

    @Test
    void handleValidationExceptionReturnsFieldErrors() {
        ValidationException ex =
                new ValidationException(List.of(new ValidationException.ValidationError("model", "不能为空")));

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", ErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().getProperties()).containsKey("errors");
    }

    @Test
    void handleUnknownExceptionIncludesTraceId() {
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-123");

        ResponseEntity<ProblemDetail> response = handler.handleException(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties())
                .containsEntry("errorCode", ErrorCode.SYSTEM_ERROR)
                .containsEntry("traceId", "trace-123");
        assertThat(response.getBody().getDetail()).contains("trace-123");
    }

    @Test
    void handleUnknownExceptionWithoutTraceIdOmitsTraceProperty() {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);

        ResponseEntity<ProblemDetail> response = handler.handleException(new IllegalStateException("boom"), request);

        assertThat(response.getBody().getProperties()).doesNotContainKey("traceId");
        assertThat(response.getBody().getDetail()).doesNotContain("traceId=");
    }
}
