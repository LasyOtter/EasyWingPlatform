package com.easywing.platform.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.ExecutionMode;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.ExecutionResult;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.RiskLevel;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.SandboxConfig;
import com.easywing.platform.ai.sandbox.CodeExecutionSandbox.SecurityCheckResult;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CodeExecutionSandboxTest {

    private final CodeExecutionSandbox sandbox = new CodeExecutionSandbox(new AiProperties());

    @Test
    void unsupportedLanguageReturnsError() {
        StepVerifier.create(sandbox.executeCode("print('hi')", "ruby", null))
                .expectError(IllegalArgumentException.class)
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
        SandboxConfig config =
                SandboxConfig.builder().mode(ExecutionMode.PROCESS).build();

        ExecutionResult result = sandbox.executeCode("import os\nos.system('ls')", "python", null, config)
                .block();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("安全检查");
    }
}
