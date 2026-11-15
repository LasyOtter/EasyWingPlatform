package com.easywing.platform.ai.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.gateway.AiGatewayController;
import com.easywing.platform.ai.model.ChatCompletionRequest;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.multiagent.MultiAgentRequest;
import com.easywing.platform.ai.rag.RagService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiRequestValidationTest {

    private static Validator validator;
    private static ValidatorFactory factory;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    @Test
    void validChatCompletionRequestShouldHaveNoViolations() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.builder()
                        .role(ChatMessage.ROLE_USER)
                        .content("hello")
                        .build()))
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void chatCompletionRequestShouldRejectBlankModelAndEmptyMessages() {
        ChatCompletionRequest request =
                ChatCompletionRequest.builder().model(" ").messages(List.of()).build();

        Set<ConstraintViolation<ChatCompletionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("model"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("messages"));
    }

    @Test
    void agentRequestShouldRejectBlankPrompt() {
        AgentRequest request = AgentRequest.builder().prompt(" ").build();

        Set<ConstraintViolation<AgentRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("prompt"));
    }

    @Test
    void multiAgentRequestShouldRejectEmptyAgentsAndBlankTask() {
        MultiAgentRequest request =
                MultiAgentRequest.builder().agentIds(List.of()).task(" ").build();

        Set<ConstraintViolation<MultiAgentRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("agentIds"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("task"));
    }

    @Test
    void ragRequestShouldRejectBlankQueryAndInvalidTopK() {
        RagService.RagRequest request =
                RagService.RagRequest.builder().query(" ").topK(0).build();

        Set<ConstraintViolation<RagService.RagRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("query"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("topK"));
    }

    @Test
    void toolExecuteRequestShouldRejectBlankToolName() {
        AiGatewayController.ToolExecuteRequest request = new AiGatewayController.ToolExecuteRequest(" ", null);

        Set<ConstraintViolation<AiGatewayController.ToolExecuteRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("toolName"));
    }

    @Test
    void skillChainRequestShouldRejectEmptySkillNames() {
        AiGatewayController.SkillChainRequest request =
                new AiGatewayController.SkillChainRequest(null, null, "input", List.of(), null);

        Set<ConstraintViolation<AiGatewayController.SkillChainRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("skillNames"));
    }
}
