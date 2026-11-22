package com.easywing.platform.ai.sandbox;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 代码执行沙箱 - 在子进程中执行用户代码
 * <p>
 * <b>默认关闭。</b>当前实现为 {@link ProcessBuilder} 子进程隔离，没有容器边界：
 * {@code maxMemoryMB} 只是声明值，无强制手段；{@code DANGEROUS_PATTERNS} 是字符串
 * 黑名单，可被 base64/反射/拼接轻易绕过（见类内注释）。在公网可达的 AI 服务上，
 * 该 Bean 等价于远程代码执行。
 * <p>
 * 启用条件：{@code easywing.ai.sandbox.enabled=true}，且仅当 AI 服务不直接暴露
 * 给不可信调用方、或已用容器/沙箱替代本实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnProperty(prefix = "easywing.ai.sandbox", name = "enabled", havingValue = "true")
@Service
public class CodeExecutionSandbox {

    private final ExecutorService executorService;
    private final Map<String, LanguageConfig> languageConfigs;
    private final SandboxConfig config;

    /**
     * 无论是否开放文件/网络都必须拦截的高危调用（进程执行、反射、退出等）。
     * <p>
     * 注意：字符串匹配只能拦住直白的写法，无法防御混淆绕过。
     * 生产环境必须使用容器隔离并配置网络/文件系统策略。
     */
    private static final List<String> ALWAYS_DANGEROUS_PATTERNS = List.of(
            "exec(",
            "eval(",
            "__import__",
            "importlib",
            "getattr(",
            "setattr(",
            "os.system",
            "os.popen",
            "subprocess",
            "child_process",
            "ctypes",
            "multiprocessing",
            "fork(",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "System.exit");

    /** 仅在未开放文件系统访问时拦截的特征。 */
    private static final List<String> FILE_PATTERNS =
            List.of("import os", "require('fs')", "require(\"fs\")", "java.io", "java.nio.file", "Files.");

    /** 仅在未开放网络访问时拦截的特征。 */
    private static final List<String> NETWORK_PATTERNS =
            List.of("import socket", "require('net')", "urllib", "requests.", "http.client");

    public CodeExecutionSandbox() {
        this(new SandboxProperties());
    }

    @Autowired
    public CodeExecutionSandbox(SandboxProperties properties) {
        this.executorService = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "code-sandbox");
            thread.setDaemon(true);
            return thread;
        });
        this.languageConfigs = initializeLanguageConfigs();
        this.config = SandboxConfig.builder()
                .maxExecutionTime(properties.getMaxExecutionTime())
                .maxMemoryMB(properties.getMaxMemoryMB())
                .maxOutputSize(properties.getMaxOutputSize())
                .allowNetworkAccess(properties.isAllowNetworkAccess())
                .allowFileSystemAccess(properties.isAllowFileSystemAccess())
                .maxInputFiles(properties.getMaxInputFiles())
                .maxInputFileSize(properties.getMaxInputFileSize())
                .maxInputTotalBytes(properties.getMaxInputTotalBytes())
                .maxOutputFiles(properties.getMaxOutputFiles())
                .maxOutputFileSize(properties.getMaxOutputFileSize())
                .maxOutputTotalBytes(properties.getMaxOutputTotalBytes())
                .build();
        if (properties.getMode() != null
                && !"process".equalsIgnoreCase(properties.getMode().trim())) {
            log.warn("sandbox.mode={} 尚未实现，当前仅支持 process 模式", properties.getMode());
        }
    }

    /**
     * 以当前配置为基准，应用请求级覆盖（仅覆盖非空项）。
     */
    public SandboxConfig configWithOverrides(
            Boolean allowFileSystemAccess, Boolean allowNetworkAccess, Long maxExecutionTime) {
        if (maxExecutionTime != null && maxExecutionTime <= 0) {
            throw new IllegalArgumentException("maxExecutionTime 必须为正数");
        }
        SandboxConfig.SandboxConfigBuilder builder = SandboxConfig.builder()
                .maxExecutionTime(this.config.getMaxExecutionTime())
                .maxMemoryMB(this.config.getMaxMemoryMB())
                .maxOutputSize(this.config.getMaxOutputSize())
                .allowNetworkAccess(this.config.isAllowNetworkAccess())
                .allowFileSystemAccess(this.config.isAllowFileSystemAccess())
                .maxInputFiles(this.config.getMaxInputFiles())
                .maxInputFileSize(this.config.getMaxInputFileSize())
                .maxInputTotalBytes(this.config.getMaxInputTotalBytes())
                .maxOutputFiles(this.config.getMaxOutputFiles())
                .maxOutputFileSize(this.config.getMaxOutputFileSize())
                .maxOutputTotalBytes(this.config.getMaxOutputTotalBytes());
        if (allowFileSystemAccess != null) {
            builder.allowFileSystemAccess(allowFileSystemAccess);
        }
        if (allowNetworkAccess != null) {
            builder.allowNetworkAccess(allowNetworkAccess);
        }
        if (maxExecutionTime != null) {
            builder.maxExecutionTime(maxExecutionTime);
        }
        return builder.build();
    }

    /**
     * 释放线程池资源，避免应用关闭后线程残留
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * 初始化语言配置
     */
    private Map<String, LanguageConfig> initializeLanguageConfigs() {
        Map<String, LanguageConfig> configs = new HashMap<>();

        // Python
        configs.put(
                "python",
                LanguageConfig.builder()
                        .language("python")
                        .fileExtension(".py")
                        .compileCommand(null) // 解释型语言
                        .executeCommand(List.of("python", "{file}"))
                        .timeoutMultiplier(1.0)
                        .build());

        // JavaScript (Node.js)
        configs.put(
                "javascript",
                LanguageConfig.builder()
                        .language("javascript")
                        .fileExtension(".js")
                        .compileCommand(null)
                        .executeCommand(List.of("node", "{file}"))
                        .timeoutMultiplier(1.0)
                        .build());

        // Java
        configs.put(
                "java",
                LanguageConfig.builder()
                        .language("java")
                        .fileExtension(".java")
                        .compileCommand(List.of("javac", "{file}"))
                        .executeCommand(List.of("java", "-cp", "{dir}", "Main"))
                        .timeoutMultiplier(1.5)
                        .build());

        // Go
        configs.put(
                "go",
                LanguageConfig.builder()
                        .language("go")
                        .fileExtension(".go")
                        .compileCommand(List.of("go", "build", "-o", "{output}", "{file}"))
                        .executeCommand(List.of("{output}"))
                        .timeoutMultiplier(1.2)
                        .build());

        // C
        configs.put(
                "c",
                LanguageConfig.builder()
                        .language("c")
                        .fileExtension(".c")
                        .compileCommand(List.of("gcc", "-o", "{output}", "{file}"))
                        .executeCommand(List.of("{output}"))
                        .timeoutMultiplier(1.2)
                        .build());

        // C++（同时接受 c++ 别名）
        LanguageConfig cpp = LanguageConfig.builder()
                .language("cpp")
                .fileExtension(".cpp")
                .compileCommand(List.of("g++", "-o", "{output}", "{file}"))
                .executeCommand(List.of("{output}"))
                .timeoutMultiplier(1.5)
                .build();
        configs.put("cpp", cpp);
        configs.put("c++", cpp);

        // PHP（解释型）
        configs.put(
                "php",
                LanguageConfig.builder()
                        .language("php")
                        .fileExtension(".php")
                        .compileCommand(null)
                        .executeCommand(List.of("php", "{file}"))
                        .timeoutMultiplier(1.0)
                        .build());

        // Rust
        configs.put(
                "rust",
                LanguageConfig.builder()
                        .language("rust")
                        .fileExtension(".rs")
                        .compileCommand(List.of("rustc", "-o", "{output}", "{file}"))
                        .executeCommand(List.of("{output}"))
                        .timeoutMultiplier(1.5)
                        .build());

        // Fortran
        configs.put(
                "fortran",
                LanguageConfig.builder()
                        .language("fortran")
                        .fileExtension(".f90")
                        .compileCommand(List.of("gfortran", "-o", "{output}", "{file}"))
                        .executeCommand(List.of("{output}"))
                        .timeoutMultiplier(1.2)
                        .build());

        // TypeScript（编译为 JS 后交给 Node 执行）
        configs.put(
                "typescript",
                LanguageConfig.builder()
                        .language("typescript")
                        .fileExtension(".ts")
                        .compileCommand(List.of("tsc", "{file}"))
                        .executeCommand(List.of("node", "{dir}/Main.js"))
                        .timeoutMultiplier(1.5)
                        .build());

        return configs;
    }

    /**
     * 返回当前支持的语言标识（已排序），供 API 与前端展示。
     */
    public List<String> getSupportedLanguages() {
        return languageConfigs.keySet().stream().sorted().toList();
    }

    /**
     * 返回指定语言的配置，不支持时返回空。
     */
    public Optional<LanguageConfig> getLanguageConfig(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(languageConfigs.get(language.trim().toLowerCase()));
    }

    /**
     * 执行代码
     */
    public Mono<ExecutionResult> executeCode(String code, String language, String input) {
        return executeCode(code, language, input, Map.of(), config);
    }

    /**
     * 执行代码（带配置）
     */
    public Mono<ExecutionResult> executeCode(String code, String language, String input, SandboxConfig customConfig) {
        return executeCode(code, language, input, Map.of(), customConfig);
    }

    /**
     * 执行代码（带配置与上传文件）。
     *
     * @param files 文件名到内容的映射；文件名必须是安全 basename，大小与数量受配置限制
     */
    public Mono<ExecutionResult> executeCode(
            String code, String language, String input, Map<String, byte[]> files, SandboxConfig customConfig) {

        LanguageConfig langConfig = languageConfigs.get(language.toLowerCase());
        if (langConfig == null) {
            return Mono.error(new IllegalArgumentException("Unsupported language: " + language));
        }
        try {
            validateFiles(files, customConfig);
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }

        log.info("Executing {} code, length: {}, files: {}", language, code.length(), files == null ? 0 : files.size());

        return Mono.fromCallable(() -> executeInSandbox(code, langConfig, input, files, customConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(
                        (long) (customConfig.getMaxExecutionTime() * langConfig.getTimeoutMultiplier())))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Code execution timeout");
                    return Mono.just(ExecutionResult.builder()
                            .success(false)
                            .error("Execution timeout: exceeded " + customConfig.getMaxExecutionTime() + "ms")
                            .executionTime(customConfig.getMaxExecutionTime())
                            .build());
                });
    }

    /**
     * 在沙箱中执行
     */
    private ExecutionResult executeInSandbox(
            String code,
            LanguageConfig langConfig,
            String input,
            Map<String, byte[]> files,
            SandboxConfig sandboxConfig) {

        Path tempDir = null;
        long startTime = System.currentTimeMillis();

        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("sandbox_");
            log.debug("Created sandbox directory: {}", tempDir);

            // 写入代码文件
            Path codeFile = createCodeFile(tempDir, code, langConfig);

            // 静态安全检查按配置分级：文件/网络特征仅在对应能力关闭时拦截。
            SecurityCheckResult security = checkCodeSecurityBlocking(code, langConfig.getLanguage(), sandboxConfig);
            if (security != null && !security.isSafe()) {
                log.warn("Code blocked by sandbox security check: {}", security.getIssues());
                return ExecutionResult.builder()
                        .success(false)
                        .error("代码未通过安全检查: " + String.join(", ", security.getIssues()))
                        .executionTime(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 写入用户上传的输入文件
            writeInputFiles(tempDir, files);

            // 编译（如果需要）
            if (langConfig.getCompileCommand() != null) {
                ExecutionOutput compileOutput = compile(codeFile, langConfig, sandboxConfig);
                if (!compileOutput.isSuccess()) {
                    return ExecutionResult.builder()
                            .success(false)
                            .error("Compilation failed: " + compileOutput.getStderr())
                            .executionTime(System.currentTimeMillis() - startTime)
                            .build();
                }
            }

            // 执行前快照，用于识别程序产生/修改的结果文件
            Map<String, Long> before = snapshotFiles(tempDir);

            // 执行
            ExecutionOutput execOutput = execute(tempDir, codeFile, langConfig, input, sandboxConfig);
            List<OutputFile> outputFiles = collectOutputFiles(tempDir, before, sandboxConfig);

            return ExecutionResult.builder()
                    .success(execOutput.isSuccess())
                    .output(execOutput.getStdout())
                    .error(execOutput.getStderr())
                    .exitCode(execOutput.getExitCode())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .memoryUsed(estimateMemoryUsage())
                    .outputFiles(outputFiles)
                    .build();

        } catch (Exception e) {
            log.error("Execution failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .error("Execution failed: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();

        } finally {
            // 清理临时目录
            if (tempDir != null) {
                cleanupDirectory(tempDir);
            }
        }
    }

    /**
     * 校验上传文件：数量、单文件大小、总大小与文件名安全。
     */
    private void validateFiles(Map<String, byte[]> files, SandboxConfig sandboxConfig) {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (files.size() > sandboxConfig.getMaxInputFiles()) {
            throw new IllegalArgumentException("上传文件数量超过上限: " + sandboxConfig.getMaxInputFiles());
        }
        long total = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = sanitizeFileName(entry.getKey());
            byte[] data = entry.getValue();
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("上传文件内容为空: " + name);
            }
            if (data.length > sandboxConfig.getMaxInputFileSize()) {
                throw new IllegalArgumentException(
                        "上传文件超过大小上限 " + sandboxConfig.getMaxInputFileSize() + " 字节: " + name);
            }
            total += data.length;
            if (total > sandboxConfig.getMaxInputTotalBytes()) {
                throw new IllegalArgumentException("上传文件总大小超过上限 " + sandboxConfig.getMaxInputTotalBytes() + " 字节");
            }
        }
    }

    /**
     * 把用户提供的文件名收敛为安全的 basename，拒绝路径穿越。
     */
    private String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        String name = rawName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isEmpty() || ".".equals(name) || "..".equals(name) || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("非法上传文件名: " + rawName);
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("上传文件名过长: " + rawName);
        }
        return name;
    }

    /**
     * 把上传文件写入沙箱工作目录。
     */
    private void writeInputFiles(Path dir, Map<String, byte[]> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Files.write(dir.resolve(sanitizeFileName(entry.getKey())), entry.getValue());
        }
    }

    /**
     * 记录目录内现有文件的相对路径与修改时间，作为结果文件识别基线。
     */
    private Map<String, Long> snapshotFiles(Path dir) throws IOException {
        Map<String, Long> snapshot = new HashMap<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                snapshot.put(
                        relativeKey(dir, path), Files.getLastModifiedTime(path).toMillis());
            }
        }
        return snapshot;
    }

    /**
     * 收集程序产生或修改过的文件，作为可下载结果。
     */
    private List<OutputFile> collectOutputFiles(Path dir, Map<String, Long> before, SandboxConfig sandboxConfig) {
        List<OutputFile> outputFiles = new ArrayList<>();
        long total = 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> candidates = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path path : candidates) {
                if (outputFiles.size() >= sandboxConfig.getMaxOutputFiles()) {
                    log.warn("结果文件数量超过上限 {}，已截断", sandboxConfig.getMaxOutputFiles());
                    break;
                }
                String key = relativeKey(dir, path);
                long modified;
                long size;
                try {
                    modified = Files.getLastModifiedTime(path).toMillis();
                    size = Files.size(path);
                } catch (IOException e) {
                    log.warn("读取结果文件失败: {}", path, e);
                    continue;
                }
                Long previous = before.get(key);
                if (previous != null && previous == modified) {
                    continue;
                }
                if (total + size > sandboxConfig.getMaxOutputTotalBytes()) {
                    log.warn("结果文件总大小超过上限 {} 字节，已截断", sandboxConfig.getMaxOutputTotalBytes());
                    break;
                }
                byte[] content = readLimited(path, sandboxConfig.getMaxOutputFileSize());
                boolean truncated = content.length < size;
                total += content.length;
                outputFiles.add(OutputFile.builder()
                        .name(key)
                        .size(size)
                        .mediaType(probeMediaType(key))
                        .contentBase64(Base64.getEncoder().encodeToString(content))
                        .truncated(truncated)
                        .build());
            }
        } catch (IOException e) {
            log.warn("收集结果文件失败: {}", dir, e);
        }
        return outputFiles;
    }

    private byte[] readLimited(Path path, long max) {
        try (InputStream is = Files.newInputStream(path)) {
            return is.readNBytes((int) Math.min(max, Integer.MAX_VALUE));
        } catch (IOException e) {
            log.warn("读取结果文件失败: {}", path, e);
            return new byte[0];
        }
    }

    private String relativeKey(Path dir, Path path) {
        return dir.relativize(path).toString().replace('\\', '/');
    }

    private String probeMediaType(String name) {
        try {
            String contentType = Files.probeContentType(Paths.get(name));
            if (contentType != null && !contentType.isBlank()) {
                return contentType;
            }
        } catch (IOException ignored) {
            // 回退到默认类型
        }
        return "application/octet-stream";
    }

    /**
     * 创建代码文件
     */
    private Path createCodeFile(Path dir, String code, LanguageConfig config) throws IOException {

        String fileName = "Main" + config.getFileExtension();
        Path file = dir.resolve(fileName);
        Files.writeString(file, code);
        return file;
    }

    /**
     * 编译代码
     */
    private ExecutionOutput compile(Path codeFile, LanguageConfig config, SandboxConfig sandboxConfig)
            throws Exception {

        List<String> command = buildCommand(config.getCompileCommand(), codeFile, codeFile.getParent());
        return executeCommand(command, codeFile.getParent(), null, sandboxConfig);
    }

    /**
     * 执行代码
     */
    private ExecutionOutput execute(
            Path dir, Path codeFile, LanguageConfig config, String input, SandboxConfig sandboxConfig)
            throws Exception {

        List<String> command = buildCommand(config.getExecuteCommand(), codeFile, dir);
        return executeCommand(command, dir, input, sandboxConfig);
    }

    /**
     * 构建命令参数列表
     * <p>
     * 使用列表而非字符串拼接，避免临时目录包含空格时被错误拆分，也避免命令注入。
     */
    private List<String> buildCommand(List<String> template, Path codeFile, Path dir) {
        Path output = dir.resolve(isWindows() ? "main.exe" : "main");
        List<String> command = new ArrayList<>(template.size());
        for (String token : template) {
            command.add(token.replace("{file}", codeFile.toString())
                    .replace("{dir}", dir.toString())
                    .replace("{output}", output.toString()));
        }
        return command;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("win");
    }

    /**
     * 执行命令
     */
    private ExecutionOutput executeCommand(
            List<String> command, Path workingDir, String input, SandboxConfig sandboxConfig) throws Exception {

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        pb.directory(workingDir.toFile());

        // 启动进程
        Process process = pb.start();

        // 如果有输入，写入
        if (input != null && !input.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        // 读取输出
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Future<Void> outputReader = executorService.submit(() -> {
            readStream(process.getInputStream(), stdout, sandboxConfig.getMaxOutputSize());
            return null;
        });

        Future<Void> errorReader = executorService.submit(() -> {
            readStream(process.getErrorStream(), stderr, sandboxConfig.getMaxOutputSize());
            return null;
        });

        // 等待完成
        boolean completed = process.waitFor(sandboxConfig.getMaxExecutionTime(), TimeUnit.MILLISECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new TimeoutException("Execution timeout");
        }

        // 等待输出读取完成
        outputReader.get(1, TimeUnit.SECONDS);
        errorReader.get(1, TimeUnit.SECONDS);

        int exitCode = process.exitValue();

        return ExecutionOutput.builder()
                .success(exitCode == 0)
                .stdout(stdout.toString())
                .stderr(stderr.toString())
                .exitCode(exitCode)
                .build();
    }

    /**
     * 读取流
     */
    private void readStream(InputStream is, StringBuilder buffer, int maxSize) throws IOException {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (buffer.length() + line.length() > maxSize) {
                    buffer.append("[Output truncated]");
                    break;
                }
                buffer.append(line).append("\n");
            }
        }
    }

    /**
     * 清理目录
     */
    private void cleanupDirectory(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("Failed to delete: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to cleanup directory: {}", dir, e);
        }
    }

    /**
     * 估算内存使用
     * <p>
     * 子进程内存无法通过 {@link Process} API 直接获取，返回 -1 表示不可用。
     * 生产环境应通过 cgroup / 容器限额控制，而不是依赖该值。
     */
    private long estimateMemoryUsage() {
        return -1L;
    }

    /**
     * 验证代码安全性（响应式包装）
     */
    public Mono<SecurityCheckResult> checkCodeSecurity(String code, String language) {
        return Mono.just(checkCodeSecurityBlocking(code, language));
    }

    /**
     * 同步执行安全检查（供已处于阻塞线程的调用方使用，避免无谓的响应式包装）。
     * 使用当前沙箱配置。
     */
    public SecurityCheckResult checkCodeSecurityBlocking(String code, String language) {
        return checkCodeSecurityBlocking(code, language, this.config);
    }

    /**
     * 按沙箱配置执行安全检查：文件/网络相关特征仅在对应能力关闭时拦截，
     * 高危调用（进程执行、反射、退出等）无论配置如何都拦截。
     */
    public SecurityCheckResult checkCodeSecurityBlocking(String code, String language, SandboxConfig sandboxConfig) {
        List<String> issues = new ArrayList<>();

        for (String pattern : ALWAYS_DANGEROUS_PATTERNS) {
            if (code.contains(pattern)) {
                issues.add("包含危险调用: " + pattern);
            }
        }
        if (!sandboxConfig.isAllowFileSystemAccess()) {
            for (String pattern : FILE_PATTERNS) {
                if (code.contains(pattern)) {
                    issues.add("包含未授权的文件访问: " + pattern);
                }
            }
        }
        if (!sandboxConfig.isAllowNetworkAccess()) {
            for (String pattern : NETWORK_PATTERNS) {
                if (code.contains(pattern)) {
                    issues.add("包含未授权的网络访问: " + pattern);
                }
            }
        }

        if (code.contains("while True") || code.contains("while(true)")) {
            if (!code.contains("break") && !code.contains("return")) {
                issues.add("疑似无限循环");
            }
        }

        return buildSecurityResult(code, issues);
    }

    private SecurityCheckResult buildSecurityResult(String code, List<String> issues) {
        boolean safe = issues.isEmpty();
        return SecurityCheckResult.builder()
                .safe(safe)
                .issues(issues)
                .riskLevel(safe ? RiskLevel.LOW : issues.size() > 2 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                .build();
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxConfig {
        @Builder.Default
        private long maxExecutionTime = 10000; // 毫秒

        @Builder.Default
        private int maxMemoryMB = 512; // MB

        @Builder.Default
        private int maxOutputSize = 10240; // 字节

        @Builder.Default
        private boolean allowNetworkAccess = false;

        @Builder.Default
        private boolean allowFileSystemAccess = false;

        @Builder.Default
        private int maxInputFiles = 8;

        @Builder.Default
        private long maxInputFileSize = 1024 * 1024;

        @Builder.Default
        private long maxInputTotalBytes = 2 * 1024 * 1024;

        @Builder.Default
        private int maxOutputFiles = 16;

        @Builder.Default
        private long maxOutputFileSize = 1024 * 1024;

        @Builder.Default
        private long maxOutputTotalBytes = 2 * 1024 * 1024;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageConfig {
        private String language;
        private String fileExtension;
        private List<String> compileCommand;
        private List<String> executeCommand;
        private double timeoutMultiplier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String output;
        private String error;
        private int exitCode;
        private long executionTime;
        private long memoryUsed;
        /** 程序产生或修改的结果文件（内容 base64）。 */
        private List<OutputFile> outputFiles;
    }

    /**
     * 结果文件：文件名、大小、媒体类型与 base64 内容。
     * <p>{@code truncated=true} 表示内容超过单文件上限被截断，{@code size} 仍为原始大小。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputFile {
        private String name;
        private long size;
        private String mediaType;
        private String contentBase64;
        private boolean truncated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionOutput {
        private boolean success;
        private String stdout;
        private String stderr;
        private int exitCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityCheckResult {
        private boolean safe;
        private List<String> issues;
        private RiskLevel riskLevel;
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
