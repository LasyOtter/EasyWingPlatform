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
package com.easywing.platform.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA密钥对配置
 * <p>
 * 启动时自动生成2048位RSA密钥对用于JWT签名。
 * 生产环境建议从外部密钥存储（如Vault、KMS）加载持久化密钥。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Configuration
public class RsaKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyConfig.class);

    @Bean
    public RSAKey rsaKey(AuthProperties properties) {
        String keyId = properties.getJwt().getKeyId();
        String keyPath = properties.getJwt().getKeyPath();

        if (StringUtils.hasText(keyPath)) {
            Path path = Path.of(keyPath);
            if (Files.exists(path)) {
                try {
                    RSAKey loaded = loadFromPem(path, keyId);
                    log.info("Loaded RSA key pair from {} with kid={}", path, keyId);
                    return loaded;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to load RSA key from " + path, e);
                }
            }
            log.warn("RSA key file {} not found, generating a new key pair", path);
            return generateAndPersist(path, keyId);
        }

        log.warn("easywing.auth.jwt.key-path is not configured: a new RSA key pair is generated"
                + " on every startup. Existing tokens will become invalid after restart, and"
                + " multi-instance deployments will fail signature verification. Configure a"
                + " persistent key path in production.");
        RSAKey key = generate(keyId);
        log.info("Generated RSA key pair with kid={}", keyId);
        return key;
    }

    /**
     * 生成密钥对并持久化到 PEM 文件（同时包含私钥与公钥块）
     */
    private RSAKey generateAndPersist(Path path, String keyId) {
        RSAKey key = generate(keyId);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, toPem(key), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) {
                // 非 POSIX 文件系统（如 Windows）不支持该操作
            }
            log.info("Generated and persisted RSA key pair to {}", path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist RSA key pair to " + path, e);
        }
        return key;
    }

    private RSAKey generate(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private RSAKey loadFromPem(Path path, String keyId) throws Exception {
        String pem = Files.readString(path, StandardCharsets.UTF_8);
        byte[] privateBytes = decodePemBlock(pem, "PRIVATE KEY");
        byte[] publicBytes = decodePemBlock(pem, "PUBLIC KEY");

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));

        return new RSAKey.Builder((RSAPublicKey) publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
    }

    private String toPem(RSAKey key) throws Exception {
        String privateKeyBlock = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(key.toRSAPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String publicKeyBlock = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(key.toRSAPublicKey().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        return privateKeyBlock + publicKeyBlock;
    }

    private byte[] decodePemBlock(String pem, String label) {
        String beginMarker = "-----BEGIN " + label + "-----";
        String endMarker = "-----END " + label + "-----";
        int start = pem.indexOf(beginMarker);
        int end = pem.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("PEM block not found: " + label);
        }
        String base64 = pem.substring(start + beginMarker.length(), end)
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
