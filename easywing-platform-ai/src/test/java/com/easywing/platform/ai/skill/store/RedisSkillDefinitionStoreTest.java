package com.easywing.platform.ai.skill.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisSkillDefinitionStoreTest {

    private final Map<String, Map<Object, Object>> data = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private RedisSkillDefinitionStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        doAnswer(invocation -> {
                    data.computeIfAbsent(invocation.getArgument(0), key -> new HashMap<>())
                            .put(invocation.getArgument(1), invocation.getArgument(2));
                    return null;
                })
                .when(hashOps)
                .put(anyString(), any(), any());
        when(hashOps.entries(anyString())).thenAnswer(invocation -> {
            Map<Object, Object> bucket = data.get(invocation.getArgument(0));
            return bucket == null ? new HashMap<>() : new HashMap<>(bucket);
        });
        doAnswer(invocation -> {
                    Map<Object, Object> bucket = data.get(invocation.getArgument(0));
                    if (bucket != null) {
                        bucket.remove(invocation.getArgument(1));
                    }
                    return null;
                })
                .when(hashOps)
                .delete(anyString(), any());

        store = new RedisSkillDefinitionStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void saveWritesJsonUnderSkillName() {
        store.save(definition("alpha", "描述A"));

        Map<Object, Object> bucket = data.get("ai:skills");
        assertThat(bucket).containsKey("alpha");
        assertThat(bucket.get("alpha")).isInstanceOf(String.class);
        assertThat((String) bucket.get("alpha")).contains("\"name\":\"alpha\"");
    }

    @Test
    void loadAllDeserializesPersistedDefinitions() {
        store.save(definition("alpha", "描述A"));
        store.save(definition("beta", "描述B"));

        List<SkillDefinition> loaded = store.loadAll();

        assertThat(loaded).extracting(SkillDefinition::getName).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(loaded).extracting(SkillDefinition::getDescription).contains("描述A", "描述B");
    }

    @Test
    void saveOverwritesSameSkillName() {
        store.save(definition("alpha", "旧"));
        store.save(definition("alpha", "新"));

        assertThat(store.loadAll()).hasSize(1);
        assertThat(store.loadAll().get(0).getDescription()).isEqualTo("新");
    }

    @Test
    void loadAllSkipsCorruptEntry() {
        data.computeIfAbsent("ai:skills", key -> new HashMap<>()).put("alpha", "{not-json");
        data.get("ai:skills").put("beta", serialized("beta"));

        List<SkillDefinition> loaded = store.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getName()).isEqualTo("beta");
    }

    @Test
    void saveIgnoresDefinitionWithoutName() {
        store.save(SkillDefinition.builder().build());
        store.save(null);

        assertThat(data.getOrDefault("ai:skills", Map.of())).isEmpty();
    }

    @Test
    void saveSwallowsSerializationFailure() {
        SkillDefinition invalid = SkillDefinition.builder()
                .name("alpha")
                .metadata(Map.of("bad", new Object()))
                .build();

        store.save(invalid);

        assertThat(data.getOrDefault("ai:skills", Map.of())).isEmpty();
    }

    @Test
    void deleteRemovesOnlyTargetField() {
        store.save(definition("alpha", "描述A"));
        store.save(definition("beta", "描述B"));

        store.delete("alpha");

        List<SkillDefinition> loaded = store.loadAll();
        assertThat(loaded).extracting(SkillDefinition::getName).containsExactly("beta");
    }

    @Test
    void deleteIsNoOpForMissingKey() {
        store.delete("missing");

        assertThat(data).isEmpty();
    }

    @Test
    void loadAllReturnsEmptyWhenNothingStored() {
        assertThat(store.loadAll()).isEmpty();
    }

    private SkillDefinition definition(String name, String description) {
        return SkillDefinition.builder()
                .id(name + "-id")
                .name(name)
                .description(description)
                .enabled(true)
                .tags(List.of("tag"))
                .build();
    }

    private String serialized(String name) {
        try {
            return new ObjectMapper().writeValueAsString(definition(name, "描述" + name));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
