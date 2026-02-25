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
package com.easywing.platform.gateway.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

/**
 * IP地址工具类
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    private IpUtil() {
    }

    public static String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        
        if (isValidIp(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }
        
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeaders().getFirst("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeaders().getFirst("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeaders().getFirst("HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeaders().getFirst("HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip;
        }
        
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            ip = remoteAddress.getAddress().getHostAddress();
            if (LOCALHOST_IPV6.equals(ip)) {
                ip = LOCALHOST_IPV4;
            }
            return ip;
        }
        
        return UNKNOWN;
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
            return true;
        }
        
        byte[] bytes = parseIpBytes(ip);
        if (bytes == null) {
            return false;
        }
        
        if (bytes[0] == 10) {
            return true;
        }
        
        if (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) {
            return true;
        }
        
        if (bytes[0] == 192 && bytes[1] == 168) {
            return true;
        }
        
        return false;
    }

    private static byte[] parseIpBytes(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return null;
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                bytes[i] = (byte) Integer.parseInt(parts[i]);
            }
            return bytes;
        } catch (Exception e) {
            return null;
        }
    }
}