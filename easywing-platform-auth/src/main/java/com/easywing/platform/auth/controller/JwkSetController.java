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
package com.easywing.platform.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWK Set公钥端点
 * <p>
 * 暴露RSA公钥供网关和资源服务器在线验证JWT签名。
 * 路径遵循OIDC Discovery规范：{@code /.well-known/jwks.json}
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@RestController
@Tag(name = "JWKS", description = "JWK Set公钥端点")
public class JwkSetController {

    private final RSAKey rsaKey;

    public JwkSetController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取JWK Set", description = "返回RSA公钥集合，供JWT验证方使用")
    public Map<String, Object> jwks() {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        return jwkSet.toJSONObject();
    }
}
