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
package com.easywing.platform.gateway.filter.logging;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访问日志记录对象
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class AccessLog {

    private String traceId;
    private String requestId;
    private String method;
    private String path;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private String userId;
    private String tenantId;
    private int status;
    private long requestTime;
    private long responseTime;
    private long duration;
    private long requestSize;
    private long responseSize;
    private Map<String, String> requestHeaders = new ConcurrentHashMap<>();
    private Map<String, String> responseHeaders = new ConcurrentHashMap<>();
    private String requestBody;
    private String responseBody;
    private String errorMessage;
    private String serviceId;
    private String grayVersion;

    public AccessLog() {
        this.requestTime = System.currentTimeMillis();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getRequestSize() {
        return requestSize;
    }

    public void setRequestSize(long requestSize) {
        this.requestSize = requestSize;
    }

    public long getResponseSize() {
        return responseSize;
    }

    public void setResponseSize(long responseSize) {
        this.responseSize = responseSize;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getGrayVersion() {
        return grayVersion;
    }

    public void setGrayVersion(String grayVersion) {
        this.grayVersion = grayVersion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"traceId\":\"").append(traceId).append('"')
          .append(",\"requestId\":\"").append(requestId).append('"')
          .append(",\"method\":\"").append(method).append('"')
          .append(",\"path\":\"").append(path).append('"');
        
        if (queryString != null) {
            sb.append(",\"queryString\":\"").append(queryString).append('"');
        }
        
        sb.append(",\"clientIp\":\"").append(clientIp).append('"')
          .append(",\"userId\":\"").append(userId).append('"')
          .append(",\"status\":").append(status)
          .append(",\"duration\":").append(duration)
          .append(",\"requestSize\":").append(requestSize)
          .append(",\"responseSize\":").append(responseSize);
        
        if (serviceId != null) {
            sb.append(",\"serviceId\":\"").append(serviceId).append('"');
        }
        
        if (grayVersion != null) {
            sb.append(",\"grayVersion\":\"").append(grayVersion).append('"');
        }
        
        if (errorMessage != null) {
            sb.append(",\"errorMessage\":\"").append(errorMessage).append('"');
        }
        
        sb.append('}');
        return sb.toString();
    }
}