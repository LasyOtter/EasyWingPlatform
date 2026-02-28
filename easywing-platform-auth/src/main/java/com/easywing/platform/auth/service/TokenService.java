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
package com.easywing.platform.auth.service;

import com.easywing.platform.auth.config.AuthProperties;
import com.easywing.platform.auth.domain.AuthUser;
import com.easywing.platform.auth.dto.TokenResponse;
import com.easywing.platform.auth.metrics.AuthMetrics;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT令牌服务
 * <p>
 * 负责：
 * <ul>
 *     <li>使用RS256算法签发访问令牌和刷新令牌</li>
 *     <li>验证刷新令牌合法性并签发新令牌对</li>
 *     <li>将令牌加入Redis黑名单（注销时）</li>
 * </ul>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final AuthProperties properties;
    private final RSAKey rsaKey;
    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;
    private final AuthMetrics authMetrics;

    public TokenService(AuthProperties properties, RSAKey rsaKey,
                        StringRedisTemplate redisTemplate,
                        UserDetailsService userDetailsService,
                        AuthMetrics authMetrics) {
        this.properties = properties;
        this.rsaKey = rsaKey;
        this.redisTemplate = redisTemplate;
        this.userDetailsService = userDetailsService;
        this.authMetrics = authMetrics;
    }

    public TokenResponse issueTokenPair(AuthUser user) {
        Timer.Sample sample = authMetrics.startTokenIssuance();
        try {
            String accessToken = buildToken(user, TOKEN_TYPE_ACCESS, properties.getJwt().getAccessTokenTtl().getSeconds());
            String refreshToken = buildToken(user, TOKEN_TYPE_REFRESH, properties.getJwt().getRefreshTokenTtl().getSeconds());

            authMetrics.recordTokenIssuance(sample, "PAIR");
            authMetrics.recordLoginSuccess(user.getUsername(), "PASSWORD");

            return new TokenResponse(accessToken, refreshToken, properties.getJwt().getAccessTokenTtl().getSeconds());
        } catch (Exception e) {
            authMetrics.recordLoginFailure(user.getUsername(), "TOKEN_BUILD_ERROR");
            throw e;
        }
    }

    public TokenResponse refreshTokenPair(String rawRefreshToken) {
        SignedJWT jwt = parseAndValidate(rawRefreshToken);
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!TOKEN_TYPE_REFRESH.equals(claims.getStringClaim("token_type"))) {
                throw new IllegalArgumentException("Not a refresh token");
            }
            if (isBlacklisted(rawRefreshToken)) {
                throw new IllegalArgumentException("Refresh token has been revoked");
            }
            String subject = claims.getSubject();
            AuthUser user = userDetailsService.loadById(subject);
            blacklist(rawRefreshToken, claims.getExpirationTime().toInstant());
            return issueTokenPair(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid refresh token: " + e.getMessage(), e);
        }
    }

    public void revokeToken(String rawToken) {
        SignedJWT jwt = parseAndValidate(rawToken);
        try {
            Instant expiresAt = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
            blacklist(rawToken, expiresAt);
            log.debug("Token revoked: jti={}", jwt.getJWTClaimsSet().getJWTID());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to revoke token: " + e.getMessage(), e);
        }
    }

    public boolean isBlacklisted(String rawToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + rawToken));
    }

    private String buildToken(AuthUser user, String tokenType, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getUserId())
                    .issuer(properties.getJwt().getIssuer())
                    .claim("preferred_username", user.getUsername())
                    .claim(properties.getJwt().getRolesClaimName(), user.getRoles())
                    .claim(properties.getJwt().getTenantIdClaimName(), user.getTenantId())
                    .claim("token_type", tokenType)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            JWSSigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JWT", e);
        }
    }

    private SignedJWT parseAndValidate(String rawToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(rawToken);
            com.nimbusds.jose.JWSVerifier verifier =
                    new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            if (jwt.getJWTClaimsSet().getExpirationTime().toInstant().isBefore(Instant.now())) {
                throw new IllegalArgumentException("Token has expired");
            }
            return jwt;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token validation failed: " + e.getMessage(), e);
        }
    }

    private void blacklist(String rawToken, Instant expiresAt) {
        long ttlSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + rawToken,
                    "1",
                    java.time.Duration.ofSeconds(ttlSeconds)
            );
        }
    }
}
