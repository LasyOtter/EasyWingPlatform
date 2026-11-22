package com.easywing.platform.ai.skill.store;

import java.util.List;

/**
 * 技能定义存储
 * <p>
 * 内存实现供本地开发使用；Redis 实现让多个 AI 实例共享同一份技能目录，
 * 避免管理员在某一实例上的技能增删改在其他实例不生效。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface SkillDefinitionStore {

    /**
     * 读取全部技能定义。
     */
    List<SkillDefinition> loadAll();

    /**
     * 按技能名称保存（覆盖同名的既有定义）。
     */
    void save(SkillDefinition definition);

    /**
     * 按技能名称删除。
     */
    void delete(String name);
}
