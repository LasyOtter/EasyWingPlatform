package com.easywing.platform.ai.agentdef;

import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.config.AiProperties.AgentDefConfig;
import com.easywing.platform.ai.model.ChatMessage;
import com.easywing.platform.ai.session.SessionOwnership;
import com.easywing.platform.ai.skill.AgentSkill;
import com.easywing.platform.ai.skill.SkillRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Agent 定义服务：创建/更新/删除/读取/列举，以及按用户与群组共享。
 * <p>
 * 所有读写均做归属校验（同租户 + 所有者）；跨用户或跨租户访问统一返回 404，
 * 避免通过响应差异探测资源是否存在。被共享者可读取，但只有所有者可修改/删除。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AgentDefinitionService {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 20000;
    private static final int MAX_MODEL_LENGTH = 128;
    private static final int MAX_TOKEN_LENGTH = 64;

    private final AgentDefinitionStore store;
    private final AiProperties properties;
    private final SkillRegistry skillRegistry;

    public AgentDefinitionService(AgentDefinitionStore store, AiProperties properties, SkillRegistry skillRegistry) {
        this.store = store;
        this.properties = properties;
        this.skillRegistry = skillRegistry;
    }

    public Mono<AgentDefinition> create(AgentCaller caller, AgentDefinitionRequest request) {
        AgentDefConfig config = config();
        validateRequest(request, config);
        long now = System.currentTimeMillis();
        AgentDefinition definition = AgentDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .systemPrompt(request.getSystemPrompt())
                .model(trimToNull(request.getModel()))
                .tools(copyTokens(request.getTools(), config.getMaxToolsPerAgent(), "工具"))
                .skills(copyTokens(request.getSkills(), config.getMaxSkillsPerAgent(), "技能"))
                .visibility(resolveVisibility(request.getVisibility(), config))
                .ownerId(caller.userId())
                .tenantId(SessionOwnership.normalizeTenant(caller.tenantId()))
                .sharedWithUsers(new ArrayList<>())
                .sharedWithGroups(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Mono.fromCallable(() -> {
            store.save(definition);
            return definition;
        });
    }

    public Mono<AgentDefinition> update(AgentCaller caller, String id, AgentDefinitionRequest request) {
        AgentDefConfig config = config();
        return Mono.fromCallable(() -> {
            AgentDefinition existing = requireOwned(id, caller);
            validateRequest(request, config);
            existing.setName(request.getName().trim());
            existing.setDescription(trimToNull(request.getDescription()));
            existing.setSystemPrompt(request.getSystemPrompt());
            existing.setModel(trimToNull(request.getModel()));
            existing.setTools(copyTokens(request.getTools(), config.getMaxToolsPerAgent(), "工具"));
            existing.setSkills(copyTokens(request.getSkills(), config.getMaxSkillsPerAgent(), "技能"));
            existing.setVisibility(resolveVisibility(request.getVisibility(), config));
            existing.setUpdatedAt(System.currentTimeMillis());
            store.save(existing);
            return existing;
        });
    }

    public Mono<AgentDefinition> get(AgentCaller caller, String id) {
        return Mono.fromCallable(() -> requireReadable(id, caller));
    }

    public Mono<List<AgentDefinition>> list(AgentCaller caller) {
        return Mono.fromCallable(() -> {
            List<AgentDefinition> result = new ArrayList<>();
            for (AgentDefinition definition : store.listAll()) {
                if (canRead(definition, caller, SessionOwnership.normalizeTenant(caller.tenantId()))) {
                    result.add(definition);
                }
            }
            result.sort(Comparator.comparingLong(AgentDefinition::getUpdatedAt).reversed());
            return result;
        });
    }

    public Mono<Void> delete(AgentCaller caller, String id) {
        return Mono.fromRunnable(() -> {
            requireOwned(id, caller);
            store.delete(id);
        });
    }

    /**
     * 共享：向现有名单追加用户/群组（去重、限量），并同步可见性。
     */
    public Mono<AgentDefinition> share(AgentCaller caller, String id, AgentDefinitionShareRequest request) {
        AgentDefConfig config = config();
        return Mono.fromCallable(() -> {
            AgentDefinition existing = requireOwned(id, caller);
            if (request == null) {
                throw badRequest("请求体不能为空");
            }
            if (request.getSharedWithUsers() != null) {
                existing.setSharedWithUsers(
                        merge(existing.getSharedWithUsers(), request.getSharedWithUsers(), config.getMaxSharedUsers()));
            }
            if (request.getSharedWithGroups() != null) {
                existing.setSharedWithGroups(merge(
                        existing.getSharedWithGroups(), request.getSharedWithGroups(), config.getMaxSharedGroups()));
            }
            existing.setVisibility(resolveShareVisibility(request.getVisibility(), existing));
            existing.setUpdatedAt(System.currentTimeMillis());
            store.save(existing);
            return existing;
        });
    }

    /**
     * 取消共享：从名单移除；名单清空后可见性回落为 {@code PRIVATE}。
     */
    public Mono<AgentDefinition> unshare(AgentCaller caller, String id, AgentDefinitionShareRequest request) {
        return Mono.fromCallable(() -> {
            AgentDefinition existing = requireOwned(id, caller);
            if (request == null) {
                throw badRequest("请求体不能为空");
            }
            if (request.getSharedWithUsers() != null && existing.getSharedWithUsers() != null) {
                existing.getSharedWithUsers().removeAll(request.getSharedWithUsers());
            }
            if (request.getSharedWithGroups() != null && existing.getSharedWithGroups() != null) {
                existing.getSharedWithGroups().removeAll(request.getSharedWithGroups());
            }
            if (isEmpty(existing.getSharedWithUsers()) && isEmpty(existing.getSharedWithGroups())) {
                existing.setVisibility(AgentVisibility.PRIVATE.name());
            }
            existing.setUpdatedAt(System.currentTimeMillis());
            store.save(existing);
            return existing;
        });
    }

    /**
     * 机器客户端专用：列举租户内全部定义（不限所有者）。
     */
    public Mono<List<AgentDefinition>> listAll(String tenantId) {
        String tenant = SessionOwnership.normalizeTenant(tenantId);
        return Mono.fromCallable(() -> store.listAll().stream()
                .filter(definition -> SessionOwnership.normalizeTenant(definition.getTenantId())
                        .equals(tenant))
                .sorted(Comparator.comparingLong(AgentDefinition::getUpdatedAt).reversed())
                .toList());
    }

    /**
     * 机器客户端专用：删除租户内任意定义。
     */
    public Mono<Void> deleteAny(String tenantId, String id) {
        String tenant = SessionOwnership.normalizeTenant(tenantId);
        return Mono.fromRunnable(() -> {
            AgentDefinition definition = store.findById(id);
            if (definition == null
                    || !SessionOwnership.normalizeTenant(definition.getTenantId())
                            .equals(tenant)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 定义不存在");
            }
            store.delete(id);
        });
    }

    /**
     * 将 Agent 定义应用到执行请求，使「无代码搭建的自定义 Agent」真正可运行（PRD 4.2）。
     * <p>仅当 {@code request.agentId} 命中一条当前调用者可读的定义时生效：
     * <ul>
     *   <li>{@code systemPrompt} 作为 system 消息前置（首条 system 消息内容相同时跳过）；</li>
     *   <li>{@code model} 回填 {@code config.model}，调用方已显式指定时不覆盖；</li>
     *   <li>{@code tools} 写入 {@code config.tools} 白名单，执行时未列入的工具会被拒绝；</li>
     *   <li>{@code skills} 的提示词前置到用户 prompt。</li>
     * </ul>
     * 未命中任何定义时原样返回（保持既有 {@code agentId} 语义兼容）；命中定义但不可读时
     * 返回 404，避免越权使用他人定义。
     */
    public Mono<AgentRequest> apply(AgentCaller caller, AgentRequest request) {
        if (request == null || !StringUtils.hasText(request.getAgentId()) || "default".equals(request.getAgentId())) {
            return Mono.just(request);
        }
        return Mono.fromCallable(() -> {
            AgentDefinition definition = store.findById(request.getAgentId());
            if (definition == null) {
                log.debug("No agent definition matches agentId={}, skip applying", request.getAgentId());
                return request;
            }
            if (!canRead(definition, caller, SessionOwnership.normalizeTenant(caller.tenantId()))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 定义不存在");
            }
            log.info("Applying agent definition {} to execution request", definition.getId());
            return applyDefinition(definition, request);
        });
    }

    private AgentRequest applyDefinition(AgentDefinition definition, AgentRequest request) {
        boolean hasTools =
                definition.getTools() != null && !definition.getTools().isEmpty();
        boolean hasModel = StringUtils.hasText(definition.getModel())
                && (request.getConfig() == null
                        || !StringUtils.hasText(request.getConfig().getModel()));
        if (hasTools || hasModel) {
            boolean created = request.getConfig() == null;
            AgentRequest.AgentConfig config = created ? defaultConfig() : request.getConfig();
            if (created) {
                request.setConfig(config);
            }
            if (hasModel) {
                config.setModel(definition.getModel().trim());
            }
            if (hasTools) {
                config.setTools(new ArrayList<>(definition.getTools()));
            }
        }

        String systemPrompt = trimToNull(definition.getSystemPrompt());
        if (systemPrompt != null) {
            List<ChatMessage> messages =
                    request.getMessages() == null ? new ArrayList<>() : new ArrayList<>(request.getMessages());
            if (!hasSystemMessage(messages, systemPrompt)) {
                messages.add(
                        0,
                        ChatMessage.builder()
                                .role(ChatMessage.ROLE_SYSTEM)
                                .content(systemPrompt)
                                .build());
                request.setMessages(messages);
            }
        }

        String skillInstructions = resolveSkillInstructions(definition.getSkills());
        if (!skillInstructions.isBlank() && StringUtils.hasText(request.getPrompt())) {
            request.setPrompt(skillInstructions + "\n\n任务：\n" + request.getPrompt());
        }
        return request;
    }

    /**
     * 未显式指定模型与工具时的默认执行参数，与 ReAct / Function Calling 的内置兜底一致。
     */
    private static AgentRequest.AgentConfig defaultConfig() {
        return AgentRequest.AgentConfig.builder()
                .maxIterations(10)
                .maxToolCalls(20)
                .timeoutMs(60000)
                .temperature(0.7)
                .build();
    }

    private static boolean hasSystemMessage(List<ChatMessage> messages, String systemPrompt) {
        if (messages.isEmpty() || messages.get(0).getContent() == null) {
            return false;
        }
        return ChatMessage.ROLE_SYSTEM.equalsIgnoreCase(messages.get(0).getRole())
                && systemPrompt.equals(messages.get(0).getContent());
    }

    private String resolveSkillInstructions(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        return skills.stream()
                .filter(StringUtils::hasText)
                .map(skillRegistry::get)
                .filter(Objects::nonNull)
                .filter(AgentSkill::isEnabled)
                .map(AgentSkill::getPromptTemplate)
                .filter(template -> template != null && !template.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private AgentDefinition requireOwned(String id, AgentCaller caller) {
        AgentDefinition definition = store.findById(id);
        if (definition == null
                || definition.getOwnerId() == null
                || !definition.getOwnerId().equals(caller.userId())
                || !SessionOwnership.normalizeTenant(definition.getTenantId())
                        .equals(SessionOwnership.normalizeTenant(caller.tenantId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 定义不存在");
        }
        return definition;
    }

    private AgentDefinition requireReadable(String id, AgentCaller caller) {
        AgentDefinition definition = store.findById(id);
        if (!canRead(definition, caller, SessionOwnership.normalizeTenant(caller.tenantId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 定义不存在");
        }
        return definition;
    }

    private static boolean canRead(AgentDefinition definition, AgentCaller caller, String tenant) {
        if (definition == null
                || !SessionOwnership.normalizeTenant(definition.getTenantId()).equals(tenant)) {
            return false;
        }
        if (caller.userId() != null && caller.userId().equals(definition.getOwnerId())) {
            return true;
        }
        if (AgentVisibility.from(definition.getVisibility()) == AgentVisibility.PUBLIC) {
            return true;
        }
        if (contains(definition.getSharedWithUsers(), caller.userId())) {
            return true;
        }
        List<String> groups = caller.groups() == null ? List.of() : caller.groups();
        return definition.getSharedWithGroups() != null
                && definition.getSharedWithGroups().stream().anyMatch(groups::contains);
    }

    private static boolean contains(List<String> values, String expected) {
        return expected != null && values != null && values.contains(expected);
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private static void validateRequest(AgentDefinitionRequest request, AgentDefConfig config) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw badRequest("名称不能为空");
        }
        if (request.getName().length() > MAX_NAME_LENGTH) {
            throw badRequest("名称长度不能超过 " + MAX_NAME_LENGTH);
        }
        if (!StringUtils.hasText(request.getSystemPrompt())) {
            throw badRequest("系统提示词不能为空");
        }
        if (request.getSystemPrompt().length() > MAX_SYSTEM_PROMPT_LENGTH) {
            throw badRequest("系统提示词长度不能超过 " + MAX_SYSTEM_PROMPT_LENGTH);
        }
        if (request.getDescription() != null && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw badRequest("描述长度不能超过 " + MAX_DESCRIPTION_LENGTH);
        }
        if (request.getModel() != null && request.getModel().length() > MAX_MODEL_LENGTH) {
            throw badRequest("模型名长度不能超过 " + MAX_MODEL_LENGTH);
        }
        if (request.getTools() != null && request.getTools().size() > config.getMaxToolsPerAgent()) {
            throw badRequest("工具数量不能超过 " + config.getMaxToolsPerAgent());
        }
        if (request.getSkills() != null && request.getSkills().size() > config.getMaxSkillsPerAgent()) {
            throw badRequest("技能数量不能超过 " + config.getMaxSkillsPerAgent());
        }
        validateTokens(request.getTools(), "工具");
        validateTokens(request.getSkills(), "技能");
        if (request.getVisibility() != null) {
            try {
                AgentVisibility.parse(request.getVisibility());
            } catch (IllegalArgumentException e) {
                throw badRequest(e.getMessage());
            }
        }
    }

    private static void validateTokens(List<String> tokens, String label) {
        if (tokens == null) {
            return;
        }
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                throw badRequest(label + "名不能为空");
            }
            if (token.length() > MAX_TOKEN_LENGTH) {
                throw badRequest(label + "名长度不能超过 " + MAX_TOKEN_LENGTH);
            }
        }
    }

    private static List<String> copyTokens(List<String> values, int max, String label) {
        if (values == null) {
            return new ArrayList<>();
        }
        if (values.size() > max) {
            throw badRequest(label + "数量不能超过 " + max);
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> merge(List<String> current, List<String> additions, int max) {
        Set<String> merged = new LinkedHashSet<>();
        if (current != null) {
            current.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (additions != null) {
            additions.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (merged.size() > max) {
            throw badRequest("共享数量不能超过 " + max);
        }
        return new ArrayList<>(merged);
    }

    private static String resolveVisibility(String requested, AgentDefConfig config) {
        if (StringUtils.hasText(requested)) {
            return AgentVisibility.parse(requested).name();
        }
        return AgentVisibility.from(config.getDefaultVisibility()).name();
    }

    private static String resolveShareVisibility(String requested, AgentDefinition existing) {
        if (StringUtils.hasText(requested)) {
            return AgentVisibility.parse(requested).name();
        }
        boolean onlyGroups = isEmpty(existing.getSharedWithUsers()) && !isEmpty(existing.getSharedWithGroups());
        return onlyGroups ? AgentVisibility.GROUP.name() : AgentVisibility.USER.name();
    }

    private AgentDefConfig config() {
        AgentDefConfig config = properties.getAgentDef();
        return config == null ? new AgentDefConfig() : config;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
