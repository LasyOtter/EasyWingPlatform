package com.easywing.platform.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.skill.store.SkillDefinition;
import com.easywing.platform.ai.skill.store.SkillDefinitionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SkillRegistryTest {

    private RecordingStore store;
    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        registry = new SkillRegistry(store);
    }

    @Test
    void registerWritesThroughToStore() {
        registry.register(skill("alpha", "描述A"));

        assertThat(store.definitions).containsKey("alpha");
        assertThat(store.definitions.get("alpha").getDescription()).isEqualTo("描述A");
        assertThat(registry.contains("alpha")).isTrue();
    }

    @Test
    void registerSkipsNullName() {
        registry.register(AgentSkill.builder().build());

        assertThat(registry.size()).isZero();
        assertThat(store.definitions).isEmpty();
    }

    @Test
    void registerIfAbsentDoesNotOverrideExistingRuntimeSkill() {
        registry.register(skill("alpha", "原始"));
        registry.registerIfAbsent(skill("alpha", "内置默认"));

        assertThat(registry.get("alpha").getDescription()).isEqualTo("原始");
    }

    @Test
    void registerIfAbsentDoesNotOverridePersistedDefinition() {
        // 模拟管理员已在持久化层关闭并改名该内置技能
        store.definitions.put(
                "alpha",
                SkillDefinition.builder()
                        .name("alpha")
                        .enabled(false)
                        .description("管理员改过")
                        .build());
        registry.restore(new ArrayList<>(store.definitions.values()), name -> executor());

        registry.registerIfAbsent(skill("alpha", "内置默认"));

        assertThat(registry.get("alpha").isEnabled()).isFalse();
        assertThat(store.definitions.get("alpha").getDescription()).isEqualTo("管理员改过");
    }

    @Test
    void restoreResolvesExecutorByNameAndDoesNotWriteBack() {
        store.definitions.put(
                "alpha",
                SkillDefinition.builder()
                        .name("alpha")
                        .description("持久化")
                        .enabled(true)
                        .build());
        int savesBefore = store.saves;

        registry.restore(new ArrayList<>(store.definitions.values()), name -> "alpha".equals(name) ? executor() : null);

        assertThat(registry.get("alpha").getDescription()).isEqualTo("持久化");
        assertThat(registry.get("alpha").getExecutor()).isNotNull();
        assertThat(store.saves).isEqualTo(savesBefore);
    }

    @Test
    void restoreKeepsNullExecutorForPromptOnlySkill() {
        store.definitions.put(
                "prompt_only", SkillDefinition.builder().name("prompt_only").build());

        registry.restore(List.of(store.definitions.get("prompt_only")), name -> null);

        assertThat(registry.get("prompt_only").getExecutor()).isNull();
    }

    @Test
    void restoreSkipsNullNameAndDuplicate() {
        List<SkillDefinition> definitions = new ArrayList<>();
        definitions.add(SkillDefinition.builder().build());
        definitions.add(SkillDefinition.builder().name("alpha").build());
        registry.register(skill("alpha", "已有"));

        registry.restore(definitions, name -> executor());

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.get("alpha").getDescription()).isEqualTo("已有");
    }

    @Test
    void unregisterDeletesPersistedDefinition() {
        registry.register(skill("alpha", "描述A"));

        registry.unregister("alpha");

        assertThat(registry.contains("alpha")).isFalse();
        assertThat(store.definitions).doesNotContainKey("alpha");
    }

    @Test
    void getAllReturnsSnapshot() {
        registry.register(skill("alpha", "描述A"));
        List<AgentSkill> all = registry.getAll();

        assertThat(all).hasSize(1);
        assertThat(all).isNotSameAs(registry.getAll());
    }

    private AgentSkill skill(String name, String description) {
        return AgentSkill.builder()
                .id(name + "-id")
                .name(name)
                .description(description)
                .category("测试")
                .enabled(true)
                .triggerMode("MANUAL")
                .priority(5)
                .toolNames(List.of("echo"))
                .executor(executor())
                .composable(true)
                .tags(List.of("t"))
                .build();
    }

    private SkillExecutor executor() {
        return new SkillExecutor() {
            @Override
            public Mono<SkillResult> execute(SkillContext context) {
                return Mono.empty();
            }
        };
    }

    private static final class RecordingStore implements SkillDefinitionStore {

        private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();
        private int saves;

        @Override
        public List<SkillDefinition> loadAll() {
            return List.copyOf(definitions.values());
        }

        @Override
        public void save(SkillDefinition definition) {
            saves++;
            definitions.put(definition.getName(), definition);
        }

        @Override
        public void delete(String name) {
            definitions.remove(name);
        }
    }
}
