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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC 数据源：按配置周期执行 SQL，把结果集映射为 {@link DataRow}。
 *
 * <p>驱动解析顺序：{@code driver} 显式指定 → 从 jdbcUrl 推断 → 线程上下文类加载器。
 * 需要数据库驱动依赖（如 mysql-connector-j）在 classpath 中。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public final class JdbcDataSource implements DataSource {

    private final String id;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String sql;
    private final String driver;
    private final int timeoutSeconds;
    private final List<String> params = new ArrayList<>();

    private volatile javax.sql.DataSource pool;

    public JdbcDataSource(String id, Map<String, Object> p) {
        this.id = id;
        this.jdbcUrl = str(p, "jdbcUrl", null);
        this.username = str(p, "username", null);
        this.password = str(p, "password", null);
        this.sql = str(p, "sql", null);
        this.driver = str(p, "driver", null);
        this.timeoutSeconds = (int) num(p, "timeoutSeconds", 5);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new DataSourceException("jdbc", id, "缺少 jdbcUrl");
        }
        if (sql == null || sql.isBlank()) {
            throw new DataSourceException("jdbc", id, "缺少 sql");
        }
        Object ps = p.get("params");
        if (ps instanceof List<?> list) {
            for (Object o : list) {
                params.add(o == null ? null : o.toString());
            }
        }
        resolveDriver();
    }

    @Override
    public String type() {
        return "jdbc";
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public synchronized void close() {
        if (pool instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // 关闭失败不影响进程退出
            }
        }
        pool = null;
    }

    @Override
    public DataRow.Result fetch(FetchContext ctx) {
        try (java.sql.Connection conn = open()) {
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(timeoutSeconds);
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    DataRow.Result result = new DataRow.Result(id);
                    while (rs.next()) {
                        DataRow row = new DataRow();
                        for (int c = 1; c <= cols; c++) {
                            String label = meta.getColumnLabel(c);
                            Object v = rs.getObject(c);
                            row.put(label, v == null ? null : v.toString());
                        }
                        result.add(row);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            throw new DataSourceException("jdbc", id, "查询失败: " + e.getMessage(), e);
        }
    }

    private java.sql.Connection open() throws Exception {
        if (pool != null) {
            return pool.getConnection();
        }
        if (poolClass != null) {
            try {
                com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
                cfg.setJdbcUrl(jdbcUrl);
                cfg.setUsername(username == null ? "" : username);
                cfg.setPassword(password == null ? "" : password);
                cfg.setMaximumPoolSize(2);
                cfg.setMinimumIdle(1);
                cfg.setMaxLifetime(10 * 60_000L);
                cfg.setConnectionTimeout(timeoutSeconds * 1000L);
                com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(cfg);
                pool = ds;
                return ds.getConnection();
            } catch (RuntimeException e) {
                throw new DataSourceException("jdbc", id, "连接池初始化失败: " + e.getMessage(), e);
            }
        }
        String user = username == null ? "" : username;
        String pwd = password == null ? "" : password;
        return java.sql.DriverManager.getConnection(jdbcUrl, user, pwd);
    }

    /** HikariCP 是否存在（可选依赖，缺失时降级）。 */
    private static final Class<?> poolClass = loadPoolClass();

    private static Class<?> loadPoolClass() {
        try {
            return Class.forName("com.zaxxer.hikari.HikariDataSource");
        } catch (Throwable e) {
            return null;
        }
    }

    private void resolveDriver() {
        if (driver != null && !driver.isBlank()) {
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new DataSourceException("jdbc", id, "驱动类不存在: " + driver, e);
            }
            return;
        }
        String inferred = inferDriver(jdbcUrl);
        if (inferred != null) {
            try {
                Class.forName(inferred);
            } catch (ClassNotFoundException e) {
                throw new DataSourceException("jdbc", id, "推断驱动 " + inferred + " 不在 classpath，请添加对应数据库驱动依赖", e);
            }
        }
    }

    /** 按 jdbcUrl 前缀推断驱动类名。 */
    static String inferDriver(String url) {
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (url.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        return null;
    }

    private static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        String s = v.toString();
        return expandEnv(s);
    }

    /** 展开 ${VAR} / ${VAR:-default} 形式的环境变量占位。 */
    static String expandEnv(String text) {
        if (text == null || text.indexOf("${") < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start < 0) {
                sb.append(text, i, text.length());
                break;
            }
            int end = text.indexOf('}', start + 2);
            if (end < 0) {
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, start);
            String expr = text.substring(start + 2, end);
            String key = expr;
            String def = null;
            int sep = expr.indexOf(":-");
            if (sep >= 0) {
                key = expr.substring(0, sep);
                def = expr.substring(sep + 2);
            }
            String val = System.getenv(key.trim());
            sb.append(val != null ? val : (def != null ? def : ""));
            i = end + 1;
        }
        return sb.toString();
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
