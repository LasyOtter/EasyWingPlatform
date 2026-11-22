package com.easywing.platform.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.ExecutionResult;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.OutputFile;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.RiskLevel;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.SandboxConfig;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.SecurityCheckResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CodeExecutionSandboxTest {

    private final CodeExecutionSandbox sandbox = new CodeExecutionSandbox();

    @Test
    void unsupportedLanguageReturnsError() {
        StepVerifier.create(sandbox.executeCode("print('hi')", "ruby", null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void supportedLanguagesCoverPrdLanguageList() {
        assertThat(sandbox.getSupportedLanguages())
                .contains("python", "javascript", "typescript", "go", "c", "cpp", "java", "php", "rust", "fortran");
    }

    @Test
    void languageConfigResolvesAliasesAndBlankInput() {
        assertThat(sandbox.getLanguageConfig("c++")).isPresent();
        assertThat(sandbox.getLanguageConfig("c++").get().getFileExtension()).isEqualTo(".cpp");
        assertThat(sandbox.getLanguageConfig("  PHP  ").get().getCompileCommand())
                .isNull();
        assertThat(sandbox.getLanguageConfig("ruby")).isEmpty();
        assertThat(sandbox.getLanguageConfig(" ")).isEmpty();
        assertThat(sandbox.getLanguageConfig(null)).isEmpty();
    }

    @Test
    void unsupportedLanguageErrorCarriesLanguageName() {
        StepVerifier.create(sandbox.executeCode("puts 1", "ruby", null))
                .expectErrorMessage("Unsupported language: ruby")
                .verify();
    }

    @Test
    void securityCheckDetectsDangerousCode() {
        SecurityCheckResult result = sandbox.checkCodeSecurity("import os\nos.system('ls')", "python")
                .block();

        assertThat(result.isSafe()).isFalse();
        assertThat(result.getIssues()).isNotEmpty();
        assertThat(result.getRiskLevel()).isIn(RiskLevel.MEDIUM, RiskLevel.HIGH);
    }

    @Test
    void securityCheckAllowsSimpleCode() {
        SecurityCheckResult result =
                sandbox.checkCodeSecurity("print('hello')", "python").block();

        assertThat(result.isSafe()).isTrue();
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void securityCheckDetectsInfiniteLoop() {
        SecurityCheckResult result =
                sandbox.checkCodeSecurity("while True:\n    pass", "python").block();

        assertThat(result.isSafe()).isFalse();
    }

    @Test
    void processModeBlocksDangerousCode() {
        SandboxConfig config = SandboxConfig.builder().build();

        ExecutionResult result = sandbox.executeCode("import os\nos.system('ls')", "python", null, config)
                .block();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("安全检查");
    }

    @Test
    void configWithOverridesAppliesOnlyNonNullValues() {
        SandboxConfig base = sandbox.configWithOverrides(null, null, null);
        assertThat(base.isAllowFileSystemAccess()).isFalse();
        assertThat(base.isAllowNetworkAccess()).isFalse();
        assertThat(base.getMaxExecutionTime()).isEqualTo(10000L);

        SandboxConfig overridden = sandbox.configWithOverrides(true, true, 500L);
        assertThat(overridden.isAllowFileSystemAccess()).isTrue();
        assertThat(overridden.isAllowNetworkAccess()).isTrue();
        assertThat(overridden.getMaxExecutionTime()).isEqualTo(500L);
    }

    @Test
    void configWithOverridesRejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> sandbox.configWithOverrides(null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxExecutionTime");
    }

    @Test
    void fileAccessPatternsAllowedOnlyWhenCapabilityEnabled() {
        SandboxConfig closed = SandboxConfig.builder().build();
        SandboxConfig open = sandbox.configWithOverrides(true, true, null);

        assertThat(sandbox.checkCodeSecurityBlocking("import os\nprint(os.getcwd())", "python", open)
                        .isSafe())
                .isTrue();
        assertThat(sandbox.checkCodeSecurityBlocking("import socket", "python", open)
                        .isSafe())
                .isTrue();
        assertThat(sandbox.checkCodeSecurityBlocking("import os", "python", closed)
                        .isSafe())
                .isFalse();
        assertThat(sandbox.checkCodeSecurityBlocking("import socket", "python", closed)
                        .isSafe())
                .isFalse();
    }

    @Test
    void dangerousCallsRemainBlockedEvenWhenCapabilitiesOpen() {
        SandboxConfig open = sandbox.configWithOverrides(true, true, null);

        assertThat(sandbox.checkCodeSecurityBlocking("import os\nos.system('ls')", "python", open)
                        .isSafe())
                .isFalse();
    }

    @Test
    void fileValidationRejectsTooManyFiles() {
        SandboxConfig config = SandboxConfig.builder().maxInputFiles(2).build();
        Map<String, byte[]> files = Map.of("a.txt", new byte[] {1}, "b.txt", new byte[] {2}, "c.txt", new byte[] {3});

        StepVerifier.create(sandbox.executeCode("print(1)", "python", null, files, config))
                .expectErrorMatches(e -> e.getMessage().contains("数量超过上限"))
                .verify();
    }

    @Test
    void fileValidationRejectsOversizedFile() {
        SandboxConfig config = SandboxConfig.builder().maxInputFileSize(4).build();
        Map<String, byte[]> files = Map.of("a.txt", "12345".getBytes(StandardCharsets.UTF_8));

        StepVerifier.create(sandbox.executeCode("print(1)", "python", null, files, config))
                .expectErrorMatches(e -> e.getMessage().contains("大小上限"))
                .verify();
    }

    @Test
    void fileValidationRejectsTotalSize() {
        SandboxConfig config = SandboxConfig.builder().maxInputTotalBytes(3).build();
        Map<String, byte[]> files = Map.of("a.txt", new byte[] {1, 2}, "b.txt", new byte[] {3, 4});

        StepVerifier.create(sandbox.executeCode("print(1)", "python", null, files, config))
                .expectErrorMatches(e -> e.getMessage().contains("总大小超过上限"))
                .verify();
    }

    @Test
    void fileValidationRejectsEmptyFile() {
        Map<String, byte[]> files = Map.of("a.txt", new byte[0]);

        StepVerifier.create(sandbox.executeCode(
                        "print(1)",
                        "python",
                        null,
                        files,
                        SandboxConfig.builder().build()))
                .expectErrorMatches(e -> e.getMessage().contains("内容为空"))
                .verify();
    }

    @Test
    void fileValidationRejectsBlankAndTraversalNames() {
        Map<String, byte[]> blank = Map.of("   ", new byte[] {1});
        StepVerifier.create(sandbox.executeCode(
                        "print(1)",
                        "python",
                        null,
                        blank,
                        SandboxConfig.builder().build()))
                .expectErrorMatches(e -> e.getMessage().contains("文件名不能为空"))
                .verify();

        Map<String, byte[]> traversal = Map.of("..", new byte[] {1});
        StepVerifier.create(sandbox.executeCode(
                        "print(1)",
                        "python",
                        null,
                        traversal,
                        SandboxConfig.builder().build()))
                .expectErrorMatches(e -> e.getMessage().contains("非法上传文件名"))
                .verify();
    }

    @Test
    void executesNodeAndCollectsOutputFile() {
        Assumptions.assumeTrue(runtimeAvailable("node", "--version"));

        SandboxConfig config =
                SandboxConfig.builder().allowFileSystemAccess(true).build();
        String code = "const fs=require('fs');"
                + "const input=fs.readFileSync('in.txt','utf8').trim();"
                + "fs.writeFileSync('out.txt','got:'+input);"
                + "console.log('done');";
        Map<String, byte[]> files = Map.of("in.txt", "data".getBytes(StandardCharsets.UTF_8));

        ExecutionResult result =
                sandbox.executeCode(code, "javascript", null, files, config).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        OutputFile out = result.getOutputFiles().stream()
                .filter(f -> "out.txt".equals(f.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(out.getSize()).isEqualTo(8L);
        assertThat(out.isTruncated()).isFalse();
        assertThat(new String(Base64.getDecoder().decode(out.getContentBase64()), StandardCharsets.UTF_8))
                .isEqualTo("got:data");
    }

    @Test
    void reportsNoOutputFilesWhenProgramWritesNothing() {
        Assumptions.assumeTrue(runtimeAvailable("node", "--version"));

        SandboxConfig config =
                SandboxConfig.builder().allowFileSystemAccess(true).build();
        ExecutionResult result = sandbox.executeCode("console.log('hi')", "javascript", null, Map.of(), config)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputFiles()).isEmpty();
    }

    private static boolean runtimeAvailable(String... command) {
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
