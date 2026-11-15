package com.easywing.platform.ai.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

/**
 * 代码执行沙箱 - 安全执行用户代码
 * 注意：这是简化实现，生产环境应使用 Docker 容器隔离
 */
@Slf4j
@Service
public class CodeExecutionSandbox {

    private final ExecutorService executorService;
    private final Map<String, LanguageConfig> languageConfigs;
    private final SandboxConfig config;

    /**
     * 静态安全检查的敏感调用特征。
     * <p>
     * 注意：字符串匹配只能拦住直白的写法，无法防御混淆绕过。
     * 生产环境必须使用容器隔离并配置网络/文件系统策略。
     */
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "exec(", "eval(", "__import__", "importlib", "getattr(", "setattr(",
            "os.system", "os.popen", "subprocess", "child_process",
            "import os", "require('fs')", "require(\"fs\")",
            "import socket", "require('net')", "urllib", "requests.",
            "http.client", "ctypes", "multiprocessing", "fork(",
            "Runtime.getRuntime", "ProcessBuilder", "java.io", "java.nio.file",
            "Files.", "System.exit"
    );

    public CodeExecutionSandbox() {
        this.executorService = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "code-sandbox");
            thread.setDaemon(true);
            return thread;
        });
        this.languageConfigs = initializeLanguageConfigs();
        this.config = SandboxConfig.builder()
                .maxExecutionTime(10000)        // 10秒
                .maxMemoryMB(512)               // 512MB
                .maxOutputSize(10 * 1024)       // 10KB
                .allowNetworkAccess(false)
                .allowFileSystemAccess(false)
                .build();
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
        configs.put("python", LanguageConfig.builder()
                .language("python")
                .fileExtension(".py")
                .compileCommand(null) // 解释型语言
                .executeCommand(List.of("python", "{file}"))
                .timeoutMultiplier(1.0)
                .build());

        // JavaScript (Node.js)
        configs.put("javascript", LanguageConfig.builder()
                .language("javascript")
                .fileExtension(".js")
                .compileCommand(null)
                .executeCommand(List.of("node", "{file}"))
                .timeoutMultiplier(1.0)
                .build());

        // Java
        configs.put("java", LanguageConfig.builder()
                .language("java")
                .fileExtension(".java")
                .compileCommand(List.of("javac", "{file}"))
                .executeCommand(List.of("java", "-cp", "{dir}", "Main"))
                .timeoutMultiplier(1.5)
                .build());

        // Go
        configs.put("go", LanguageConfig.builder()
                .language("go")
                .fileExtension(".go")
                .compileCommand(List.of("go", "build", "-o", "{output}", "{file}"))
                .executeCommand(List.of("{output}"))
                .timeoutMultiplier(1.2)
                .build());

        return configs;
    }

    /**
     * 执行代码
     */
    public Mono<ExecutionResult> executeCode(
            String code,
            String language,
            String input) {

        return executeCode(code, language, input, config);
    }

    /**
     * 执行代码（带配置）
     */
    public Mono<ExecutionResult> executeCode(
            String code,
            String language,
            String input,
            SandboxConfig customConfig) {

        LanguageConfig langConfig = languageConfigs.get(language.toLowerCase());
        if (langConfig == null) {
            return Mono.error(new IllegalArgumentException("Unsupported language: " + language));
        }

        log.info("Executing {} code, length: {}", language, code.length());

        return Mono.fromCallable(() -> executeInSandbox(code, langConfig, input, customConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(
                        (long) (customConfig.getMaxExecutionTime() * langConfig.getTimeoutMultiplier())
                ))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Code execution timeout");
                    return Mono.just(ExecutionResult.builder()
                            .success(false)
                            .error("Execution timeout: exceeded " +
                                    customConfig.getMaxExecutionTime() + "ms")
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
            SandboxConfig sandboxConfig) {

        Path tempDir = null;
        long startTime = System.currentTimeMillis();

        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("sandbox_");
            log.debug("Created sandbox directory: {}", tempDir);

            // 写入代码文件
            Path codeFile = createCodeFile(tempDir, code, langConfig);

            // 当配置限制网络/文件访问时，先做静态安全检查，拦截高风险代码
            if (!sandboxConfig.isAllowNetworkAccess() || !sandboxConfig.isAllowFileSystemAccess()) {
                SecurityCheckResult security = checkCodeSecurity(code, langConfig.getLanguage()).block();
                if (security != null && !security.isSafe()) {
                    log.warn("Code blocked by sandbox security check: {}", security.getIssues());
                    return ExecutionResult.builder()
                            .success(false)
                            .error("代码未通过安全检查: " + String.join(", ", security.getIssues()))
                            .executionTime(System.currentTimeMillis() - startTime)
                            .build();
                }
            }

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

            // 执行
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
            // 清理临时目录
            if (tempDir != null) {
                cleanupDirectory(tempDir);
            }
        }
    }

    /**
     * 创建代码文件
     */
    private Path createCodeFile(Path dir, String code, LanguageConfig config)
            throws IOException {

        String fileName = "Main" + config.getFileExtension();
        Path file = dir.resolve(fileName);
        Files.writeString(file, code);
        return file;
    }

    /**
     * 编译代码
     */
    private ExecutionOutput compile(
            Path codeFile,
            LanguageConfig config,
            SandboxConfig sandboxConfig) throws Exception {

        List<String> command = buildCommand(config.getCompileCommand(), codeFile, codeFile.getParent());
        return executeCommand(command, codeFile.getParent(), null, sandboxConfig);
    }

    /**
     * 执行代码
     */
    private ExecutionOutput execute(
            Path dir,
            Path codeFile,
            LanguageConfig config,
            String input,
            SandboxConfig sandboxConfig) throws Exception {

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
            command.add(token
                    .replace("{file}", codeFile.toString())
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
            List<String> command,
            Path workingDir,
            String input,
            SandboxConfig sandboxConfig) throws Exception {

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
        boolean completed = process.waitFor(
                sandboxConfig.getMaxExecutionTime(),
                TimeUnit.MILLISECONDS
        );

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
    private void readStream(InputStream is, StringBuilder buffer, int maxSize)
            throws IOException {

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
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
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
     * 验证代码安全性
     */
    public Mono<SecurityCheckResult> checkCodeSecurity(String code, String language) {
        List<String> issues = new ArrayList<>();

        for (String pattern : DANGEROUS_PATTERNS) {
            if (code.contains(pattern)) {
                issues.add("包含危险调用: " + pattern);
            }
        }

        // 检查无限循环风险
        if (code.contains("while True") || code.contains("while(true)")) {
            if (!code.contains("break") && !code.contains("return")) {
                issues.add("疑似无限循环");
            }
        }

        boolean safe = issues.isEmpty();

        return Mono.just(SecurityCheckResult.builder()
                .safe(safe)
                .issues(issues)
                .riskLevel(safe ? RiskLevel.LOW :
                        issues.size() > 2 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                .build());
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxConfig {
        @Builder.Default
        private long maxExecutionTime = 10000;  // 毫秒
        @Builder.Default
        private int maxMemoryMB = 512;          // MB
        @Builder.Default
        private int maxOutputSize = 10240;      // 字节
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

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
