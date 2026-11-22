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
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.core.exception.SystemException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.Timer;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
    private static final String USER_TOKEN_PREFIX = "auth:user:tokens:";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final AuthProperties properties;
    private final RSAKey rsaKey;
    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;
    private final AuthMetrics authMetrics;

    public TokenService(
            AuthProperties properties,
            RSAKey rsaKey,
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
            String accessToken = buildToken(
                    user,
                    TOKEN_TYPE_ACCESS,
                    properties.getJwt().getAccessTokenTtl().getSeconds());
            String refreshToken = buildToken(
                    user,
                    TOKEN_TYPE_REFRESH,
                    properties.getJwt().getRefreshTokenTtl().getSeconds());

            addTokenToUserSet(user.getUserId(), accessToken);
            addTokenToUserSet(user.getUserId(), refreshToken);

            authMetrics.recordTokenIssuance(sample, "PAIR");
            authMetrics.recordLoginSuccess(user.getUsername(), "PASSWORD");

            return new TokenResponse(
                    accessToken,
                    refreshToken,
                    properties.getJwt().getAccessTokenTtl().getSeconds());
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
                throw new BizException(ErrorCode.INVALID_CREDENTIALS, "不是有效的刷新令牌");
            }
            if (isBlacklisted(rawRefreshToken)) {
                throw new BizException(ErrorCode.TOKEN_INVALID, "刷新令牌已被吊销");
            }
            String subject = claims.getSubject();
            AuthUser user = userDetailsService.loadById(subject);
            blacklist(rawRefreshToken, claims.getExpirationTime().toInstant());
            return issueTokenPair(user);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Refresh token validation failed", e);
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "刷新令牌无效或已过期");
        }
    }

    public void revokeToken(String rawToken) {
        SignedJWT jwt = parseAndValidate(rawToken);
        try {
            String userId = jwt.getJWTClaimsSet().getSubject();
            String jti = jwt.getJWTClaimsSet().getJWTID();
            Instant expiresAt = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
            blacklist(rawToken, expiresAt);
            removeTokenFromUserSet(userId, rawToken);
            log.debug("Token revoked: jti={}, userId={}", jti, userId);
        } catch (Exception e) {
            log.error("Failed to revoke token", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "令牌吊销失败");
        }
    }

    public boolean isBlacklisted(String rawToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + rawToken));
    }

    /**
     * 从令牌中解析用户ID
     * <p>
     * 用于让客户端只能操作自己的令牌，避免通过请求头伪造 userId 越权撤销他人会话。
     *
     * @param rawToken 已签名令牌
     * @return 令牌 subject（用户ID）
     */
    public String resolveUserId(String rawToken) {
        try {
            SignedJWT jwt = parseAndValidate(rawToken);
            return jwt.getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "无效的令牌", e);
        }
    }

    /**
     * 撤销指定用户的所有的令牌
     * <p>
     * 将用户所有当前有效的令牌加入黑名单
     *
     * @param userId 用户ID
     */
    public void revokeAllTokens(String userId) {
        String userTokenKey = USER_TOKEN_PREFIX + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userTokenKey);
        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                try {
                    SignedJWT jwt = SignedJWT.parse(token);
                    Instant expiresAt =
                            jwt.getJWTClaimsSet().getExpirationTime().toInstant();
                    blacklist(token, expiresAt);
                } catch (Exception e) {
                    log.warn("Failed to blacklist token for user {}: {}", userId, e.getMessage());
                }
            }
            redisTemplate.delete(userTokenKey);
        }
        log.info("All tokens revoked for user: {}", userId);
    }

    /**
     * 将令牌加入用户令牌集合（用于 revokeAll 时批量处理）
     *
     * @param userId 用户ID
     * @param token  令牌
     */
    public void addTokenToUserSet(String userId, String token) {
        String key = USER_TOKEN_PREFIX + userId;
        redisTemplate.opsForSet().add(key, token);
        // 刷新过期时间，避免用户令牌集合无限增长
        redisTemplate.expire(key, java.time.Duration.ofDays(7));
    }

    /**
     * 从用户令牌集合移除令牌
     *
     * @param userId 用户ID
     * @param token  令牌
     */
    public void removeTokenFromUserSet(String userId, String token) {
        redisTemplate.opsForSet().remove(USER_TOKEN_PREFIX + userId, token);
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
            throw new SystemException(ErrorCode.SYSTEM_ERROR, "令牌签发失败", e);
        }
    }

    /**
     * 为机器客户端（deployment-bound 服务身份）签发访问令牌。
     * <p>
     * subject 为 {@code client:<clientId>}，携带 {@code client_id}、租户与 {@code scope} claim，
     * 便于资源服务器映射为 {@code SCOPE_*} 权限；不签发刷新令牌（机器身份按 client_credentials 重新获取）。
     *
     * @param clientId 客户端 ID
     * @param tenant   绑定租户
     * @param scopes   已校验的 scope 集合
     * @param ttl      令牌有效期
     * @return 已签名序列化的 JWT
     */
    public String issueMachineToken(String clientId, String tenant, List<String> scopes, Duration ttl) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject("client:" + clientId)
                    .issuer(properties.getJwt().getIssuer())
                    .claim("client_id", clientId)
                    .claim(properties.getJwt().getTenantIdClaimName(), tenant)
                    .claim("token_type", TOKEN_TYPE_ACCESS)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttl)));
            if (scopes != null && !scopes.isEmpty()) {
                claims.claim("scope", String.join(" ", scopes));
            }

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims.build());
            JWSSigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Machine token issuance failed for client: {}", clientId, e);
            throw new SystemException(ErrorCode.SYSTEM_ERROR, "机器令牌签发失败", e);
        }
    }

    private SignedJWT parseAndValidate(String rawToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(rawToken);
            com.nimbusds.jose.JWSVerifier verifier =
                    new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                throw new BizException(ErrorCode.TOKEN_INVALID, "令牌签名无效");
            }
            if (jwt.getJWTClaimsSet().getExpirationTime().toInstant().isBefore(Instant.now())) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED, "令牌已过期");
            }
            return jwt;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token validation failed", e);
            throw new BizException(ErrorCode.TOKEN_INVALID, "无效的令牌");
        }
    }

    private void blacklist(String rawToken, Instant expiresAt) {
        long ttlSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + rawToken, "1", java.time.Duration.ofSeconds(ttlSeconds));
        }
    }
}
