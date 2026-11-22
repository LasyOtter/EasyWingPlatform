package com.easywing.platform.ai.skill;

import com.easywing.platform.ai.skill.impl.DataAnalysisSkill;
import com.easywing.platform.ai.skill.impl.InformationRetrievalSkill;
import com.easywing.platform.ai.skill.impl.TextProcessingSkill;
import com.easywing.platform.ai.skill.store.SkillDefinitionStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 技能自动配置 - 注册所有内置技能
 */
@Slf4j
@Configuration
public class SkillAutoConfiguration {

    /**
     * 初始化所有内置技能
     */
    @Bean
    @ConditionalOnMissingBean
    public SkillInitializer skillInitializer(
            SkillRegistry skillRegistry,
            SkillDefinitionStore skillDefinitionStore,
            InformationRetrievalSkill informationRetrievalSkill,
            DataAnalysisSkill dataAnalysisSkill,
            TextProcessingSkill textProcessingSkill) {

        log.info("Initializing built-in skills");

        // 先恢复持久化定义，管理员对内置技能的启停与配置修改才能覆盖默认值
        Map<String, SkillExecutor> executors = Map.of(
                "information_retrieval", informationRetrievalSkill,
                "data_analysis", dataAnalysisSkill,
                "text_processing", textProcessingSkill);
        skillRegistry.restore(skillDefinitionStore.loadAll(), executors::get);

        // 注册信息检索技能
        AgentSkill infoRetrievalSkill = AgentSkill.builder()
                .id(UUID.randomUUID().toString())
                .name("information_retrieval")
                .description("从多个 URL 并行检索信息并聚合结果")
                .category("信息检索")
                .enabled(true)
                .priority(10)
                .toolNames(List.of("http_request"))
                .executor(informationRetrievalSkill)
                .composable(true)
                .tags(List.of("检索", "网络", "HTTP"))
                .build();
        skillRegistry.registerIfAbsent(infoRetrievalSkill);

        // 注册数据分析技能
        AgentSkill dataAnalysisSkillDef = AgentSkill.builder()
                .id(UUID.randomUUID().toString())
                .name("data_analysis")
                .description("对数值数据进行统计分析、排序、过滤和分组")
                .category("数据分析")
                .enabled(true)
                .priority(8)
                .toolNames(List.of("data_analysis"))
                .executor(dataAnalysisSkill)
                .composable(true)
                .tags(List.of("分析", "统计", "数据"))
                .build();
        skillRegistry.registerIfAbsent(dataAnalysisSkillDef);

        // 注册文本处理技能
        AgentSkill textProcessingSkillDef = AgentSkill.builder()
                .id(UUID.randomUUID().toString())
                .name("text_processing")
                .description("对文本进行各种处理操作，包括转换、替换、分割等")
                .category("文本处理")
                .enabled(true)
                .priority(7)
                .toolNames(List.of("string_manipulation"))
                .executor(textProcessingSkill)
                .composable(true)
                .tags(List.of("文本", "字符串", "处理"))
                .build();
        skillRegistry.registerIfAbsent(textProcessingSkillDef);

        log.info("Skill initialization completed, {} skills registered", skillRegistry.size());

        return new SkillInitializer(skillRegistry);
    }

    /**
     * 技能初始化器
     */
    public static class SkillInitializer {
        private final SkillRegistry skillRegistry;

        public SkillInitializer(SkillRegistry skillRegistry) {
            this.skillRegistry = skillRegistry;
            log.info(
                    "Skills available: {}",
                    skillRegistry.getAll().stream().map(AgentSkill::getName).toList());
        }

        public SkillRegistry getSkillRegistry() {
            return skillRegistry;
        }
    }
}
