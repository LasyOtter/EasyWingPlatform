package com.easywing.platform.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PromptServiceTest {

    private PromptService service;

    @BeforeEach
    void setUp() {
        service = new PromptService(new InMemoryPromptStore());
    }

    @Test
    void createsPromptAndExtractsVariables() {
        PromptDefinition prompt = service.create("u1", "default", "周报", "帮我总结 {{项目}} 的 {{周期}} 进展", "办公")
                .block();

        assertThat(prompt.getId()).isNotBlank();
        assertThat(prompt.getTenantId()).isEqualTo("default");
        assertThat(prompt.getVariables()).containsExactly("周期", "项目");
        assertThat(prompt.getCategory()).isEqualTo("办公");
    }

    @Test
    void rejectsBlankTitleOrContent() {
        assertThatThrownBy(
                        () -> service.create("u1", "default", " ", "内容", null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        assertThatThrownBy(
                        () -> service.create("u1", "default", "标题", " ", null).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rendersVariables() {
        PromptDefinition prompt =
                service.create("u1", "default", "周报", "帮我总结 {{项目}} 的进展", null).block();

        String rendered = service.render("u1", "default", prompt.getId(), Map.of("项目", "EasyWing"))
                .block();

        assertThat(rendered).isEqualTo("帮我总结 EasyWing 的进展");
    }

    @Test
    void renderFailsOnMissingVariable() {
        PromptDefinition prompt =
                service.create("u1", "default", "周报", "你好 {{姓名}}", null).block();

        assertThatThrownBy(() -> service.render("u1", "default", prompt.getId(), Map.of())
                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRecomputesVariables() {
        PromptDefinition prompt =
                service.create("u1", "default", "旧", "固定文本", null).block();
        PromptDefinition updated = service.update("u1", "default", prompt.getId(), "新标题", "新 {{变量}} 文本", null)
                .block();

        assertThat(updated.getTitle()).isEqualTo("新标题");
        assertThat(updated.getVariables()).containsExactly("变量");
        assertThat(updated.getUpdatedAt()).isGreaterThanOrEqualTo(prompt.getCreatedAt());
    }

    @Test
    void isolatesUsers() {
        PromptDefinition prompt =
                service.create("u1", "default", "私有", "内容", null).block();

        assertThatThrownBy(() -> service.get("u2", "default", prompt.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        assertThat(service.list("u2", "default").block()).isEmpty();
    }

    @Test
    void isolatesTenants() {
        PromptDefinition prompt =
                service.create("u1", "tenant-a", "私有", "内容", null).block();

        assertThatThrownBy(() -> service.get("u1", "tenant-b", prompt.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesPrompt() {
        PromptDefinition prompt =
                service.create("u1", "default", "待删", "内容", null).block();

        service.delete("u1", "default", prompt.getId()).block();

        assertThatThrownBy(() -> service.get("u1", "default", prompt.getId()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void listsByUpdateTimeDescending() {
        PromptDefinition first =
                service.create("u1", "default", "一", "内容一", null).block();
        PromptDefinition second =
                service.create("u1", "default", "二", "内容二", null).block();

        List<PromptDefinition> prompts = service.list("u1", "default").block();

        assertThat(prompts).extracting(PromptDefinition::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void extractVariablesDeduplicates() {
        assertThat(PromptService.extractVariables("{{a}} 与 {{a}} 和 {{b}}")).containsExactly("a", "b");
    }
}
