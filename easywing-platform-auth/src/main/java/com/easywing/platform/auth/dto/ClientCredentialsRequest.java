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
package com.easywing.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 机器客户端 client_credentials 请求体（RFC 6749 §4.4 等价实现）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public class ClientCredentialsRequest {

    @JsonProperty("client_id")
    @NotBlank(message = "client_id 不能为空")
    private String clientId;

    @JsonProperty("client_secret")
    @NotBlank(message = "client_secret 不能为空")
    private String clientSecret;

    /** 空格分隔的 scope 列表；留空表示申请该客户端授权的全部 scope */
    private String scope;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
