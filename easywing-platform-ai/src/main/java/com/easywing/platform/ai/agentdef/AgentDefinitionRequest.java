package com.easywing.platform.ai.agentdef;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建/更新 Agent 定义的请求体。
 * <p>
 * 长度上限在服务层结合 {@code easywing.ai.agent-def.*} 配置校验，此处仅做字段级约束。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinitionRequest {

    @Size(max = 128, message = "名称长度不能超过 128")
    private String name;

    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    @Size(max = 20000, message = "系统提示词长度不能超过 20000")
    private String systemPrompt;

    @Size(max = 128, message = "模型名长度不能超过 128")
    private String model;

    @Size(max = 50, message = "工具数量超出上限")
    private List<String> tools;

    @Size(max = 20, message = "技能数量超出上限")
    private List<String> skills;

    private String visibility;
}
