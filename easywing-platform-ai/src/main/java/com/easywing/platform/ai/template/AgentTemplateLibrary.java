package com.easywing.platform.ai.template;

import com.easywing.platform.ai.agent.AgentRequest;
import com.easywing.platform.ai.multiagent.AgentRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 模板库 - 预置的 Agent 模板和最佳实践
 */
@Slf4j
@Service
public class AgentTemplateLibrary {

    private final ObjectMapper objectMapper;
    private final Map<String, AgentTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, WorkflowTemplate> workflows = new ConcurrentHashMap<>();

    public AgentTemplateLibrary(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        initializeTemplates();
        initializeWorkflows();
    }

    /**
     * 初始化模板
     */
    private void initializeTemplates() {
        // 1. 代码审查模板
        templates.put("code_review", AgentTemplate.builder()
                .id("code_review")
                .name("代码审查助手")
                .description("专业的代码审查，检查代码质量、安全性、性能问题")
                .category("开发")
                .difficulty("中级")
                .estimatedTime("5-10分钟")
                .systemPrompt("""
                        你是一个专业的代码审查专家。审查代码时需要关注：
                        1. 代码质量：可读性、可维护性、设计模式
                        2. 安全性：常见安全漏洞、输入验证
                        3. 性能：时间复杂度、空间复杂度、潜在性能问题
                        4. 最佳实践：语言特定的最佳实践

                        对每个问题提供：
                        - 严重程度（Critical/High/Medium/Low）
                        - 问题描述
                        - 修复建议
                        - 代码示例
                        """)
                .promptTemplate("""
                        请审查以下 {language} 代码：

                        ```{language}
                        {code}
                        ```

                        请从代码质量、安全性、性能三个维度进行审查。
                        """)
                .variables(List.of("language", "code"))
                .requiredTools(List.of())
                .tags(List.of("代码审查", "质量保证", "开发"))
                .usageCount(0)
                .rating(4.8)
                .build());

        // 2. 技术文档生成模板
        templates.put("doc_generator", AgentTemplate.builder()
                .id("doc_generator")
                .name("技术文档生成器")
                .description("自动生成高质量的技术文档、API 文档、README")
                .category("文档")
                .difficulty("初级")
                .estimatedTime("3-5分钟")
                .systemPrompt("""
                        你是一个技术文档专家。生成的文档应该：
                        1. 结构清晰：使用合适的标题层级
                        2. 内容完整：包含必要的说明和示例
                        3. 格式规范：使用 Markdown 格式
                        4. 易于理解：避免过于技术化的表述
                        """)
                .promptTemplate("""
                        请为以下项目生成技术文档：

                        项目名称：{projectName}
                        项目描述：{description}
                        主要功能：{features}

                        文档类型：{docType}

                        请生成结构清晰、内容完整的文档。
                        """)
                .variables(List.of("projectName", "description", "features", "docType"))
                .requiredTools(List.of())
                .tags(List.of("文档", "技术写作"))
                .usageCount(0)
                .rating(4.5)
                .build());

        // 3. 数据分析助手模板
        templates.put("data_analyst", AgentTemplate.builder()
                .id("data_analyst")
                .name("数据分析助手")
                .description("专业的数据分析，提供洞察和可视化建议")
                .category("数据分析")
                .difficulty("中级")
                .estimatedTime("5-15分钟")
                .systemPrompt("""
                        你是一个数据分析专家。分析数据时需要：
                        1. 数据探索：了解数据分布、异常值、缺失值
                        2. 统计分析：计算关键指标、趋势分析
                        3. 可视化：建议合适的图表类型
                        4. 洞察：发现数据中的模式和规律
                        """)
                .promptTemplate("""
                        请分析以下数据：

                        数据描述：{dataDescription}
                        数据样本：{dataSample}
                        分析目标：{goal}

                        请提供完整的数据分析报告，包括统计指标、趋势分析和可视化建议。
                        """)
                .variables(List.of("dataDescription", "dataSample", "goal"))
                .requiredTools(List.of("data_analysis", "calculator"))
                .tags(List.of("数据分析", "统计", "可视化"))
                .usageCount(0)
                .rating(4.7)
                .build());

        // 4. 问题诊断模板
        templates.put("troubleshooter", AgentTemplate.builder()
                .id("troubleshooter")
                .name("问题诊断专家")
                .description("系统性地诊断和解决技术问题")
                .category("运维")
                .difficulty("高级")
                .estimatedTime("10-20分钟")
                .systemPrompt("""
                        你是一个问题诊断专家。诊断问题时遵循：
                        1. 收集信息：错误日志、系统状态、重现步骤
                        2. 分析原因：逐步缩小范围，定位根本原因
                        3. 提供方案：临时解决方案和长期修复方案
                        4. 预防措施：避免类似问题再次发生
                        """)
                .promptTemplate("""
                        问题描述：{problemDescription}
                        错误信息：{errorMessage}
                        系统环境：{environment}

                        请系统性地诊断问题并提供解决方案。
                        """)
                .variables(List.of("problemDescription", "errorMessage", "environment"))
                .requiredTools(List.of())
                .tags(List.of("问题诊断", "故障排查", "运维"))
                .usageCount(0)
                .rating(4.9)
                .build());

        // 5. 学习导师模板
        templates.put("learning_mentor", AgentTemplate.builder()
                .id("learning_mentor")
                .name("学习导师")
                .description("个性化的学习指导和知识讲解")
                .category("教育")
                .difficulty("初级")
                .estimatedTime("5-10分钟")
                .systemPrompt("""
                        你是一个耐心的学习导师。讲解知识时：
                        1. 由浅入深：从基础概念开始，逐步深入
                        2. 举例说明：使用生动的例子和类比
                        3. 互动引导：通过提问引导思考
                        4. 总结巩固：定期总结关键要点
                        """)
                .promptTemplate("""
                        主题：{topic}
                        学习者水平：{level}
                        学习目标：{goal}

                        请提供个性化的学习指导。
                        """)
                .variables(List.of("topic", "level", "goal"))
                .requiredTools(List.of())
                .tags(List.of("教育", "学习", "辅导"))
                .usageCount(0)
                .rating(4.6)
                .build());

        log.info("Initialized {} agent templates", templates.size());
    }

    /**
     * 初始化工作流模板
     */
    private void initializeWorkflows() {
        // 完整开发流程
        workflows.put("full_dev_cycle", WorkflowTemplate.builder()
                .id("full_dev_cycle")
                .name("完整开发流程")
                .description("从需求分析到部署的完整开发流程")
                .category("开发")
                .steps(List.of(
                        WorkflowStep.builder()
                                .stepNumber(1)
                                .name("需求分析")
                                .templateId("planner")
                                .description("分析需求，制定开发计划")
                                .build(),
                        WorkflowStep.builder()
                                .stepNumber(2)
                                .name("技术设计")
                                .templateId("tech_expert")
                                .description("设计技术方案和架构")
                                .build(),
                        WorkflowStep.builder()
                                .stepNumber(3)
                                .name("代码实现")
                                .templateId("executor")
                                .description("实现功能代码")
                                .build(),
                        WorkflowStep.builder()
                                .stepNumber(4)
                                .name("代码审查")
                                .templateId("code_review")
                                .description("审查代码质量")
                                .build(),
                        WorkflowStep.builder()
                                .stepNumber(5)
                                .name("文档生成")
                                .templateId("doc_generator")
                                .description("生成技术文档")
                                .build()
                ))
                .estimatedTime("30-60分钟")
                .difficulty("高级")
                .tags(List.of("开发", "完整流程"))
                .build());

        log.info("Initialized {} workflow templates", workflows.size());
    }

    /**
     * 获取模板
     */
    public Optional<AgentTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * 获取所有模板
     */
    public List<AgentTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * 按分类获取模板
     */
    public List<AgentTemplate> getTemplatesByCategory(String category) {
        return templates.values().stream()
                .filter(t -> t.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    /**
     * 搜索模板
     */
    public List<AgentTemplate> searchTemplates(String query) {
        String lowerQuery = query.toLowerCase();
        return templates.values().stream()
                .filter(t ->
                        t.getName().toLowerCase().contains(lowerQuery) ||
                        t.getDescription().toLowerCase().contains(lowerQuery) ||
                        t.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery))
                )
                .collect(Collectors.toList());
    }

    /**
     * 获取热门模板
     */
    public List<AgentTemplate> getPopularTemplates(int limit) {
        return templates.values().stream()
                .sorted(Comparator.comparingInt(AgentTemplate::getUsageCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取高评分模板
     */
    public List<AgentTemplate> getTopRatedTemplates(int limit) {
        return templates.values().stream()
                .sorted(Comparator.comparingDouble(AgentTemplate::getRating).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 从模板创建请求
     */
    public Mono<AgentRequest> createRequestFromTemplate(
            String templateId,
            Map<String, String> variables) {

        AgentTemplate template = templates.get(templateId);
        if (template == null) {
            return Mono.error(new IllegalArgumentException("模板不存在: " + templateId));
        }

        // 填充变量
        String filledPrompt = fillTemplate(template.getPromptTemplate(), variables);

        // 构建请求
        AgentRequest request = AgentRequest.builder()
                .agentId(templateId)
                .prompt(filledPrompt)
                .messages(template.getSystemPrompt() != null ?
                        List.of(com.easywing.platform.ai.model.ChatMessage.builder()
                                .role("system")
                                .content(template.getSystemPrompt())
                                .build()) : List.of())
                .config(AgentRequest.AgentConfig.builder()
                        .model("gpt-4o")
                        .maxToolCalls(10)
                        .temperature(0.7)
                        .build())
                .build();

        // 增加使用计数
        template.setUsageCount(template.getUsageCount() + 1);

        return Mono.just(request);
    }

    /**
     * 填充模板
     */
    private String fillTemplate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 添加自定义模板
     */
    public void addCustomTemplate(AgentTemplate template) {
        templates.put(template.getId(), template);
        log.info("Added custom template: {}", template.getId());
    }

    /**
     * 评分模板
     */
    public void rateTemplate(String templateId, double rating) {
        AgentTemplate template = templates.get(templateId);
        if (template != null) {
            // 简单的平均评分更新
            double currentRating = template.getRating();
            int usageCount = template.getUsageCount();
            double newRating = (currentRating * usageCount + rating) / (usageCount + 1);
            template.setRating(newRating);
        }
    }

    /**
     * 获取模板分类
     */
    public List<String> getCategories() {
        return templates.values().stream()
                .map(AgentTemplate::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ========== 数据类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentTemplate {
        private String id;
        private String name;
        private String description;
        private String category;
        private String difficulty;
        private String estimatedTime;
        private String systemPrompt;
        private String promptTemplate;
        private List<String> variables;
        private List<String> requiredTools;
        private List<String> tags;
        private int usageCount;
        private double rating;
        private String author;
        private long createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowTemplate {
        private String id;
        private String name;
        private String description;
        private String category;
        private List<WorkflowStep> steps;
        private String estimatedTime;
        private String difficulty;
        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStep {
        private int stepNumber;
        private String name;
        private String templateId;
        private String description;
        private Map<String, String> defaultVariables;
    }
}
