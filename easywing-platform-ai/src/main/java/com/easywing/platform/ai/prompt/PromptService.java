package com.easywing.platform.ai.prompt;

import com.easywing.platform.ai.session.SessionOwnership;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Prompt 库服务：模板的增删改查与 {@code {{变量}}} 渲染。
 * <p>
 * 所有操作均做归属校验（同租户 + 所有者），跨用户访问返回 404。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
public class PromptService {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([^{}]+)}}");

    private static final String DEFAULT_CATEGORY = "default";

    private final PromptStore store;

    public PromptService(PromptStore store) {
        this.store = store;
    }

    public Mono<PromptDefinition> create(
            String userId, String tenantId, String title, String content, String category) {
        validate(title, content);
        long now = System.currentTimeMillis();
        PromptDefinition prompt = PromptDefinition.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .tenantId(SessionOwnership.normalizeTenant(tenantId))
                .title(title.trim())
                .content(content)
                .category(StringUtils.hasText(category) ? category.trim() : DEFAULT_CATEGORY)
                .variables(extractVariables(content))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Mono.fromCallable(() -> {
            store.save(prompt);
            return prompt;
        });
    }

    /**
     * 更新模板：仅覆盖非空字段；正文变更时重新解析变量。
     */
    public Mono<PromptDefinition> update(
            String userId, String tenantId, String id, String title, String content, String category) {
        return Mono.fromCallable(() -> {
            PromptDefinition existing = requireOwned(id, userId, tenantId);
            if (StringUtils.hasText(title)) {
                existing.setTitle(title.trim());
            }
            if (StringUtils.hasText(content)) {
                existing.setContent(content);
                existing.setVariables(extractVariables(content));
            }
            if (StringUtils.hasText(category)) {
                existing.setCategory(category.trim());
            }
            existing.setUpdatedAt(System.currentTimeMillis());
            store.save(existing);
            return existing;
        });
    }

    public Mono<PromptDefinition> get(String userId, String tenantId, String id) {
        return Mono.fromCallable(() -> requireOwned(id, userId, tenantId));
    }

    public Mono<List<PromptDefinition>> list(String userId, String tenantId) {
        return Mono.fromCallable(() -> store.listByUser(userId, SessionOwnership.normalizeTenant(tenantId)));
    }

    public Mono<Void> delete(String userId, String tenantId, String id) {
        return Mono.fromRunnable(() -> {
            requireOwned(id, userId, tenantId);
            store.delete(id);
        });
    }

    /**
     * 渲染模板：替换全部 {@code {{变量}}}；存在未提供的变量时返回 400。
     */
    public Mono<String> render(String userId, String tenantId, String id, Map<String, String> variables) {
        return Mono.fromCallable(() -> {
            PromptDefinition prompt = requireOwned(id, userId, tenantId);
            Map<String, String> vars = variables == null ? Map.of() : variables;
            Matcher matcher = VARIABLE.matcher(prompt.getContent());
            StringBuffer rendered = new StringBuffer();
            Set<String> missing = new LinkedHashSet<>();
            while (matcher.find()) {
                String name = matcher.group(1).trim();
                String value = vars.get(name);
                if (value == null) {
                    missing.add(name);
                    continue;
                }
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(rendered);
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少变量: " + String.join(", ", missing));
            }
            return rendered.toString();
        });
    }

    /** 从正文解析变量名，去重并按字典序返回。 */
    static List<String> extractVariables(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1).trim());
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private PromptDefinition requireOwned(String id, String userId, String tenantId) {
        PromptDefinition prompt = store.findById(id);
        if (prompt == null
                || prompt.getUserId() == null
                || !prompt.getUserId().equals(userId)
                || !SessionOwnership.normalizeTenant(prompt.getTenantId())
                        .equals(SessionOwnership.normalizeTenant(tenantId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt 不存在");
        }
        return prompt;
    }

    private static void validate(String title, String content) {
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能为空");
        }
    }
}
