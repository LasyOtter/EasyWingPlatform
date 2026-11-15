package com.easywing.platform.ai.skill;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 技能执行服务 - 负责技能的执行和编排
 */
@Slf4j
@Service
public class SkillExecutionService {

    private final SkillRegistry skillRegistry;

    public SkillExecutionService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 执行单个技能
     */
    public Mono<SkillResult> executeSkill(String skillName, SkillContext context) {
        log.info("Executing skill: {}", skillName);

        AgentSkill skill = skillRegistry.get(skillName);
        if (skill == null) {
            return Mono.just(SkillResult.failure("技能不存在: " + skillName));
        }

        if (!skill.isEnabled()) {
            return Mono.just(SkillResult.failure("技能已禁用: " + skillName));
        }

        SkillExecutor executor = skill.getExecutor();
        if (executor == null) {
            return Mono.just(SkillResult.failure("技能执行器未配置: " + skillName));
        }

        // 验证参数
        if (!executor.validateParameters(context.getParameters())) {
            return Mono.just(SkillResult.failure("参数验证失败"));
        }

        long startTime = System.currentTimeMillis();

        return executor.execute(context)
                .doOnSuccess(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    result.setExecutionTimeMs(executionTime);
                    log.info("Skill {} executed in {}ms", skillName, executionTime);
                })
                .doOnError(e -> log.error("Skill execution failed: {}", skillName, e))
                .onErrorResume(e -> Mono.just(SkillResult.failure("执行错误: " + e.getMessage())));
    }

    /**
     * 执行技能链 - 按依赖顺序执行多个技能
     */
    public Mono<SkillResult> executeSkillChain(List<String> skillNames, SkillContext context) {
        log.info("Executing skill chain: {}", skillNames);

        if (skillNames == null || skillNames.isEmpty()) {
            return Mono.just(SkillResult.failure("技能链为空"));
        }

        // 解析依赖并排序
        List<String> sortedSkills = resolveDependencies(skillNames);
        if (sortedSkills == null) {
            return Mono.just(SkillResult.failure("技能依赖存在循环"));
        }

        // 初始化中间结果
        if (context.getIntermediateResults() == null) {
            context.setIntermediateResults(new HashMap<>());
        }

        // 顺序执行技能链
        return executeChainRecursively(sortedSkills, 0, context);
    }

    /**
     * 递归执行技能链
     */
    private Mono<SkillResult> executeChainRecursively(List<String> skillNames, int index, SkillContext context) {

        if (index >= skillNames.size()) {
            // 所有技能执行完成
            return Mono.just(SkillResult.success(context.getIntermediateResults(), "技能链执行完成"));
        }

        String currentSkill = skillNames.get(index);
        return executeSkill(currentSkill, context).flatMap(result -> {
            if (!result.isSuccess()) {
                return Mono.just(result);
            }

            // 保存中间结果
            context.getIntermediateResults().put(currentSkill, result.getData());

            // 更新技能链
            if (context.getSkillChain() == null) {
                context.setSkillChain(new ArrayList<>());
            }
            context.getSkillChain().add(currentSkill);

            // 执行下一个技能
            return executeChainRecursively(skillNames, index + 1, context);
        });
    }

    /**
     * 组合多个技能 - 并行执行并合并结果
     */
    public Mono<SkillResult> composeSkills(List<String> skillNames, SkillContext context) {
        log.info("Composing skills: {}", skillNames);

        if (skillNames == null || skillNames.isEmpty()) {
            return Mono.just(SkillResult.failure("技能列表为空"));
        }

        List<Mono<SkillResult>> skillMonos = skillNames.stream()
                .map(skillName -> executeSkill(skillName, context))
                .collect(Collectors.toList());

        return Mono.zip(skillMonos, results -> {
            Map<String, Object> combinedResults = new HashMap<>();
            List<String> toolsUsed = new ArrayList<>();
            long totalTime = 0;

            for (int i = 0; i < results.length; i++) {
                SkillResult result = (SkillResult) results[i];
                if (!result.isSuccess()) {
                    return SkillResult.failure("技能执行失败: " + skillNames.get(i) + " - " + result.getError());
                }

                combinedResults.put(skillNames.get(i), result.getData());
                if (result.getToolsUsed() != null) {
                    toolsUsed.addAll(result.getToolsUsed());
                }
                totalTime += result.getExecutionTimeMs();
            }

            return SkillResult.builder()
                    .success(true)
                    .data(combinedResults)
                    .message("所有技能执行成功")
                    .executionTimeMs(totalTime)
                    .toolsUsed(toolsUsed)
                    .build();
        });
    }

    /**
     * 解析技能依赖并进行拓扑排序
     */
    private List<String> resolveDependencies(List<String> skillNames) {
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 构建依赖图
        for (String skillName : skillNames) {
            AgentSkill skill = skillRegistry.get(skillName);
            if (skill == null) continue;

            graph.putIfAbsent(skillName, new ArrayList<>());
            inDegree.putIfAbsent(skillName, 0);

            if (skill.getDependencies() != null) {
                for (String dep : skill.getDependencies()) {
                    if (skillNames.contains(dep)) {
                        graph.get(dep).add(skillName);
                        inDegree.put(skillName, inDegree.getOrDefault(skillName, 0) + 1);
                    }
                }
            }
        }

        // 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (String skill : skillNames) {
            if (inDegree.getOrDefault(skill, 0) == 0) {
                queue.offer(skill);
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);

            for (String neighbor : graph.getOrDefault(current, List.of())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检查是否存在循环依赖
        if (sorted.size() != skillNames.size()) {
            log.error("Circular dependency detected in skills: {}", skillNames);
            return null;
        }

        return sorted;
    }

    /**
     * 推荐技能 - 根据用户输入推荐合适的技能
     */
    public List<AgentSkill> recommendSkills(String input, int limit) {
        List<AgentSkill> allSkills =
                skillRegistry.getAll().stream().filter(AgentSkill::isEnabled).collect(Collectors.toList());

        // 简单的关键词匹配评分
        return allSkills.stream()
                .map(skill -> {
                    int score = calculateRelevanceScore(skill, input);
                    return new ScoredSkill(skill, score);
                })
                .filter(scored -> scored.score > 0)
                .sorted(Comparator.comparingInt(ScoredSkill::getScore).reversed())
                .limit(limit)
                .map(ScoredSkill::getSkill)
                .collect(Collectors.toList());
    }

    private int calculateRelevanceScore(AgentSkill skill, String input) {
        String lowerInput = input.toLowerCase();
        int score = 0;

        // 名称匹配
        if (lowerInput.contains(skill.getName().toLowerCase())) {
            score += 10;
        }

        // 描述匹配
        if (skill.getDescription() != null
                && lowerInput.contains(skill.getDescription().toLowerCase())) {
            score += 5;
        }

        // 标签匹配
        if (skill.getTags() != null) {
            for (String tag : skill.getTags()) {
                if (lowerInput.contains(tag.toLowerCase())) {
                    score += 3;
                }
            }
        }

        return score;
    }

    private static class ScoredSkill {
        private final AgentSkill skill;
        private final int score;

        public ScoredSkill(AgentSkill skill, int score) {
            this.skill = skill;
            this.score = score;
        }

        public AgentSkill getSkill() {
            return skill;
        }

        public int getScore() {
            return score;
        }
    }
}
