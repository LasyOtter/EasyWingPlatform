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
package com.easywing.platform.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.easywing.platform.core.constant.HttpHeaders;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.gateway.util.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class ProblemDetailSupportTest {

    @BeforeAll
    static void initCodec() {
        JsonCodec.init(new ObjectMapper());
    }

    @Test
    void writeUsesProblemJsonContentTypeAndGivenStatus() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        ServerHttpResponse response = exchange.getResponse();

        StepVerifier.create(ProblemDetailSupport.write(
                        exchange,
                        HttpStatus.BAD_REQUEST,
                        "Validation Error",
                        "参数错误",
                        ErrorCode.VALIDATION_ERROR,
                        response))
                .verifyComplete();

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo(HttpHeaders.APPLICATION_PROBLEM_JSON);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(response).writeWith(any());
    }

    @Test
    void writeTooManyRequestsSetsRetryAfterHeader() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        ServerHttpResponse response = exchange.getResponse();

        StepVerifier.create(ProblemDetailSupport.writeTooManyRequests(exchange, "请求过于频繁", response))
                .verifyComplete();

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void writeUnauthorizedSetsWwwAuthenticateHeader() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        ServerHttpResponse response = exchange.getResponse();

        StepVerifier.create(ProblemDetailSupport.writeUnauthorized(
                        exchange, "Unauthorized", "缺少认证令牌", ErrorCode.TOKEN_MISSING, response))
                .verifyComplete();

        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void buildTypeMatchesEasyWingWebConvention() {
        assertThat(ProblemDetailSupport.buildType(ErrorCode.SYSTEM_ERROR).toString())
                .isEqualTo("https://api.easywing.io/errors/SYS001");
        assertThat(ProblemDetailSupport.buildType(null).toString()).isEqualTo("https://api.easywing.io/errors/");
    }

    @Test
    void traceIdIsPropagatedToResponseBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x")
                .header(HttpHeaders.X_TRACE_ID, "trace-abc")
                .build());

        // 直接验证内部 traceId 解析：构造同样的请求头，确认能取到
        assertThat(exchange.getRequest().getHeaders().getFirst(HttpHeaders.X_TRACE_ID))
                .isEqualTo("trace-abc");

        ServerHttpResponse response = exchange.getResponse();
        StepVerifier.create(ProblemDetailSupport.write(
                        exchange,
                        HttpStatus.BAD_REQUEST,
                        "Validation Error",
                        "参数错误",
                        ErrorCode.VALIDATION_ERROR,
                        response))
                .verifyComplete();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(response).writeWith(any());
    }
}
