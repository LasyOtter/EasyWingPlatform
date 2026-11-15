package com.easywing.platform.ai.sandbox;

import com.easywing.platform.ai.config.AiProperties;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 代码执行沙箱 - 安全执行用户代码。
 * <p>
 * 支持两种隔离模式：
 * <ul>
 * <li>{@link ExecutionMode#PROCESS}：本机进程隔离，仅适合开发/演示，无法承受不可信代码。</li>
 * <li>{@link ExecutionMode#DOCKER}：容器隔离，禁用网络、只读根文件系统、限额内存/CPU/进程数，适合生产。</li>
 * </ul>
 * Docker 不可用时自动回退到进程模式并记录告警。
 */
@Slf4j
@Service
public class CodeExecutionSandbox {

    private static final String DOCKER_SANDBOX_DIR = "/sandbox";

    /**
     * 静态安全检查的敏感调用特征。
     * <p>
     * 注意：字符串匹配只能拦住直白的写法，无法防御混淆绕过，不能替代容器隔离。
     */
    private static final List<String> DANGEROUS_PATTERNS = List.of(
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
            "import os",
            "require('fs')",
            "require(\"fs\")",
            "import socket",
            "require('net')",
            "urllib",
            "requests.",
            "http.client",
            "ctypes",
            "multiprocessing",
            "fork(",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "java.io",
            "java.nio.file",
            "Files.",
            "System.exit");

    private final ExecutorService executorService;
    private final Map<String, LanguageConfig> languageConfigs;
    private final AiProperties aiProperties;
    private final AtomicReference<Boolean> dockerAvailable = new AtomicReference<>();

    public CodeExecutionSandbox(AiProperties aiProperties) {
        this.executorService = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "code-sandbox");
            thread.setDaemon(true);
            return thread;
        });
        this.languageConfigs = initializeLanguageConfigs();
        this.aiProperties = aiProperties;
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

        configs.put(
                "python",
                LanguageConfig.builder()
                        .language("python")
                        .fileExtension(".py")
                        .compileCommand(null)
                        .executeCommand(List.of("python", "{file}"))
                        .dockerImage("python:3.12-slim")
                        .dockerCommand(List.of("python", "Main.py"))
                        .timeoutMultiplier(1.0)
                        .build());

        configs.put(
                "javascript",
                LanguageConfig.builder()
                        .language("javascript")
                        .fileExtension(".js")
                        .compileCommand(null)
                        .executeCommand(List.of("node", "{file}"))
                        .dockerImage("node:22-slim")
                        .dockerCommand(List.of("node", "Main.js"))
                        .timeoutMultiplier(1.0)
                        .build());

        configs.put(
                "java",
                LanguageConfig.builder()
                        .language("java")
                        .fileExtension(".java")
                        .compileCommand(List.of("javac", "{file}"))
                        .executeCommand(List.of("java", "-cp", "{dir}", "Main"))
                        .dockerImage("eclipse-temurin:21-jdk")
                        .dockerCommand(List.of("sh", "-c", "javac Main.java && java -cp . Main"))
                        .timeoutMultiplier(1.5)
                        .build());

        configs.put(
                "go",
                LanguageConfig.builder()
                        .language("go")
                        .fileExtension(".go")
                        .compileCommand(List.of("go", "build", "-o", "{output}", "{file}"))
                        .executeCommand(List.of("{output}"))
                        .dockerImage("golang:1.23-alpine")
                        .dockerCommand(List.of("sh", "-c", "go build -o main Main.go && ./main"))
                        .timeoutMultiplier(1.2)
                        .build());

        return configs;
    }

    /**
     * 执行代码，使用配置中的默认沙箱配置
     */
    public Mono<ExecutionResult> executeCode(String code, String language, String input) {
        return executeCode(code, language, input, defaultConfig());
    }

    /**
     * 执行代码（带配置）
     */
    public Mono<ExecutionResult> executeCode(String code, String language, String input, SandboxConfig customConfig) {

        LanguageConfig langConfig = languageConfigs.get(language.toLowerCase());
        if (langConfig == null) {
            return Mono.error(new IllegalArgumentException("Unsupported language: " + language));
        }

        log.info("Executing {} code, length: {}", language, code.length());

        return Mono.fromCallable(() -> executeInSandbox(code, langConfig, input, customConfig))
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

    private SandboxConfig defaultConfig() {
        AiProperties.SandboxSettings settings = aiProperties.getSandbox();
        return SandboxConfig.builder()
                .mode(parseMode(settings.getMode()))
                .maxExecutionTime(settings.getMaxExecutionTime())
                .maxMemoryMB(settings.getMaxMemoryMB())
                .maxOutputSize(settings.getMaxOutputSize())
                .allowNetworkAccess(settings.isAllowNetworkAccess())
                .allowFileSystemAccess(settings.isAllowFileSystemAccess())
                .build();
    }

    private ExecutionMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return ExecutionMode.PROCESS;
        }
        try {
            return ExecutionMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown sandbox mode '{}', falling back to process", mode);
            return ExecutionMode.PROCESS;
        }
    }

    /**
     * 在沙箱中执行
     */
    private ExecutionResult executeInSandbox(
            String code, LanguageConfig langConfig, String input, SandboxConfig sandboxConfig) {

        if (sandboxConfig.getMode() == ExecutionMode.DOCKER && isDockerAvailable()) {
            return executeInDockerSandbox(code, langConfig, input, sandboxConfig);
        }
        if (sandboxConfig.getMode() == ExecutionMode.DOCKER) {
            log.warn("Docker is not available, falling back to process sandbox");
        }
        return executeInProcessSandbox(code, langConfig, input, sandboxConfig);
    }

    private ExecutionResult executeInProcessSandbox(
            String code, LanguageConfig langConfig, String input, SandboxConfig sandboxConfig) {

        Path tempDir = null;
        long startTime = System.currentTimeMillis();

        try {
            tempDir = Files.createTempDirectory("sandbox_");
            log.debug("Created sandbox directory: {}", tempDir);

            Path codeFile = createCodeFile(tempDir, code, langConfig);

            SecurityCheckResult blocked = enforceSecurity(code, langConfig.getLanguage(), sandboxConfig);
            if (blocked != null) {
                return blockedResult(blocked, startTime);
            }

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

            ExecutionOutput execOutput = execute(tempDir, codeFile, langConfig, input, sandboxConfig);

            return ExecutionResult.builder()
                    .success(execOutput.isSuccess())
                    .output(execOutput.getStdout())
                    .error(execOutput.getStderr())
                    .exitCode(execOutput.getExitCode())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .memoryUsed(estimateMemoryUsage())
                    .build();

        } catch (Exception e) {
            log.error("Execution failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .error("Execution failed: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();
        } finally {
            if (tempDir != null) {
                cleanupDirectory(tempDir);
            }
        }
    }

    private ExecutionResult executeInDockerSandbox(
            String code, LanguageConfig langConfig, String input, SandboxConfig sandboxConfig) {

        Path tempDir = null;
        long startTime = System.currentTimeMillis();

        try {
            tempDir = Files.createTempDirectory("sandbox_");
            log.debug("Created docker sandbox directory: {}", tempDir);

            createCodeFile(tempDir, code, langConfig);

            SecurityCheckResult blocked = enforceSecurity(code, langConfig.getLanguage(), sandboxConfig);
            if (blocked != null) {
                return blockedResult(blocked, startTime);
            }

            ExecutionOutput execOutput = executeInDocker(tempDir, langConfig, input, sandboxConfig);

            return ExecutionResult.builder()
                    .success(execOutput.isSuccess())
                    .output(execOutput.getStdout())
                    .error(execOutput.getStderr())
                    .exitCode(execOutput.getExitCode())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .memoryUsed(estimateMemoryUsage())
                    .build();

        } catch (Exception e) {
            log.error("Docker execution failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .error("Docker execution failed: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();
        } finally {
            if (tempDir != null) {
                cleanupDirectory(tempDir);
            }
        }
    }

    private ExecutionResult blockedResult(SecurityCheckResult security, long startTime) {
        log.warn("Code blocked by sandbox security check: {}", security.getIssues());
        return ExecutionResult.builder()
                .success(false)
                .error("代码未通过安全检查: " + String.join(", ", security.getIssues()))
                .executionTime(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * 当配置限制网络或文件访问时，执行静态安全检查；通过返回 {@code null}。
     */
    private SecurityCheckResult enforceSecurity(String code, String language, SandboxConfig sandboxConfig) {
        if (sandboxConfig.isAllowNetworkAccess() && sandboxConfig.isAllowFileSystemAccess()) {
            return null;
        }
        SecurityCheckResult security = checkCodeSecurity(code, language).block();
        return security == null || security.isSafe() ? null : security;
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
     * 在 Docker 容器中执行代码
     */
    private ExecutionOutput executeInDocker(Path dir, LanguageConfig config, String input, SandboxConfig sandboxConfig)
            throws Exception {
        List<String> command = buildDockerCommand(dir, config, sandboxConfig);
        return executeCommand(command, dir, input, sandboxConfig);
    }

    /**
     * 构建 Docker 运行命令
     * <p>
     * 禁用网络、只读根文件系统、限额内存/CPU/进程数，仅挂载代码目录用于写入。
     */
    private List<String> buildDockerCommand(Path dir, LanguageConfig config, SandboxConfig sandboxConfig) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("-i");
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(sandboxConfig.getMaxMemoryMB() + "m");
        command.add("--memory-swap");
        command.add(sandboxConfig.getMaxMemoryMB() + "m");
        command.add("--cpus");
        command.add("1");
        command.add("--pids-limit");
        command.add("64");
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,noexec,nosuid,size=64m");
        command.add("-v");
        command.add(toDockerPath(dir) + ":" + DOCKER_SANDBOX_DIR + ":w");
        command.add("-w");
        command.add(DOCKER_SANDBOX_DIR);
        command.add(config.getDockerImage());
        command.addAll(config.getDockerCommand());
        return command;
    }

    private boolean isDockerAvailable() {
        Boolean cached = dockerAvailable.get();
        if (cached != null) {
            return cached;
        }

        boolean available = false;
        try {
            Process process = new ProcessBuilder("docker", "--version")
                    .redirectErrorStream(true)
                    .start();
            available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Docker availability check failed: {}", e.getMessage());
        }

        dockerAvailable.set(available);
        return available;
    }

    /**
     * 将 Windows 路径转换为 Docker Desktop 可识别的 POSIX 挂载路径。
     */
    private String toDockerPath(Path path) {
        String value = path.toAbsolutePath().toString();
        if (value.length() > 2 && value.charAt(1) == ':') {
            return "/" + Character.toLowerCase(value.charAt(0))
                    + value.substring(2).replace('\\', '/');
        }
        return value.replace('\\', '/');
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

        Process process = pb.start();

        if (input != null && !input.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

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

        boolean completed = process.waitFor(sandboxConfig.getMaxExecutionTime(), TimeUnit.MILLISECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new TimeoutException("Execution timeout");
        }

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
     */
    private long estimateMemoryUsage() {
        return -1L;
    }

    /**
     * 验证代码安全性
     */
    public Mono<SecurityCheckResult> checkCodeSecurity(String code, String language) {
        List<String> issues = new ArrayList<>();

        for (String pattern : DANGEROUS_PATTERNS) {
            if (code.contains(pattern)) {
                issues.add("包含危险调用: " + pattern);
            }
        }

        if (code.contains("while True") || code.contains("while(true)")) {
            if (!code.contains("break") && !code.contains("return")) {
                issues.add("疑似无限循环");
            }
        }

        boolean safe = issues.isEmpty();

        return Mono.just(SecurityCheckResult.builder()
                .safe(safe)
                .issues(issues)
                .riskLevel(safe ? RiskLevel.LOW : issues.size() > 2 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                .build());
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxConfig {
        @Builder.Default
        private ExecutionMode mode = ExecutionMode.PROCESS;

        @Builder.Default
        private long maxExecutionTime = 10000;

        @Builder.Default
        private int maxMemoryMB = 512;

        @Builder.Default
        private int maxOutputSize = 10240;

        @Builder.Default
        private boolean allowNetworkAccess = false;

        @Builder.Default
        private boolean allowFileSystemAccess = false;
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
        private String dockerImage;
        private List<String> dockerCommand;
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

    public enum ExecutionMode {
        PROCESS,
        DOCKER
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
