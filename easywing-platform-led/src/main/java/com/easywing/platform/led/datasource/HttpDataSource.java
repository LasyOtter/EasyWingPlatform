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
package com.easywing.platform.led.datasource;

import com.easywing.platform.led.engine.JsonLiteAccess;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP/REST 数据源：GET/POST 拉取 JSON，按字段路径取值。
 *
 * <p><b>生命周期</b>：JDK 11+ 的 {@link HttpClient} 未实现 {@code AutoCloseable}，
 * 实例应复用（命名源全局单例，见 {@code DataSourceManager}），不要每帧新建。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class HttpDataSource implements DataSource {

    private final String id;
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String field;
    private final int timeoutSeconds;
    private final String body;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile boolean closed;

    public HttpDataSource(String id, Map<String, Object> p) {
        this.id = id;
        this.url = JdbcDataSource.expandEnv(str(p, "url", null));
        this.method = str(p, "method", "GET").toUpperCase(java.util.Locale.ROOT);
        this.field = str(p, "field", null);
        this.timeoutSeconds = (int) num(p, "timeoutSeconds", 5);
        this.body = str(p, "body", null);
        this.headers = new LinkedHashMap<>();
        Object h = p.get("headers");
        if (h instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                headers.put(String.valueOf(e.getKey()), JdbcDataSource.expandEnv(String.valueOf(e.getValue())));
            }
        }
        if (url == null || url.isBlank()) {
            throw new DataSourceException("http", id, "缺少 url");
        }
    }

    @Override
    public String type() {
        return "http";
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public DataRow.Result fetch(FetchContext ctx) {
        if (closed) {
            throw new DataSourceException("http", id, "数据源已关闭");
        }
        try {
            HttpRequest.Builder b =
                    HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds));
            headers.forEach(b::header);
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            } else {
                b.GET();
            }
            HttpResponse<String> resp =
                    http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new DataSourceException("http", id, "HTTP " + resp.statusCode());
            }
            return parse(resp.body());
        } catch (DataSourceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataSourceException("http", id, "请求被中断", e);
        } catch (Exception e) {
            throw new DataSourceException("http", id, "请求失败: " + describe(e), e);
        }
    }

    private static String describe(Throwable e) {
        String m = e.getMessage();
        if (m != null && !m.isBlank()) {
            return m;
        }
        return e.getClass().getSimpleName();
    }

    private DataRow.Result parse(String bodyText) {
        DataRow.Result result = new DataRow.Result(id);
        if (field == null || field.isBlank()) {
            result.add(new DataRow().put("value", bodyText).put("body", bodyText));
            return result;
        }
        Object root = JsonLiteAccess.parse(bodyText);
        Object target = path(root, field);
        if (target instanceof List<?> list) {
            for (Object item : list) {
                result.add(toRow(item));
            }
            return result;
        }
        if (target instanceof Map<?, ?> m) {
            result.add(toRow(m));
            return result;
        }
        String text = target == null ? "" : target.toString();
        DataRow row = new DataRow().put("value", text);
        String leaf = leafName(field);
        if (leaf != null && !leaf.isBlank() && !leaf.equals("value")) {
            row.put(leaf, text);
        }
        result.add(row);
        return result;
    }

    private static String leafName(String fieldPath) {
        if (fieldPath == null) {
            return null;
        }
        String[] parts = fieldPath.split("\\.");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    private DataRow toRow(Object item) {
        DataRow row = new DataRow();
        if (item instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object v = e.getValue();
                row.put(String.valueOf(e.getKey()), v == null ? null : v.toString());
            }
        } else {
            row.put("value", item == null ? null : item.toString());
        }
        return row;
    }

    private static Object path(Object root, String fieldPath) {
        Object cur = root;
        for (String part : fieldPath.split("\\.")) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(part);
            } else if (cur instanceof List<?> l) {
                try {
                    cur = l.get(Integer.parseInt(part));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    private static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        return v == null ? def : v.toString();
    }

    private static double num(Map<String, Object> p, String key, double def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(v.toString().trim());
    }
}
