package com.easywing.platform.ai.agentdef;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 共享/取消共享 Agent 定义的请求体。
 * <p>
 * 字段为 {@code null} 时分别表示「不改动用户列表」与「不改动群组列表」。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinitionShareRequest {

    @Size(max = 100, message = "共享用户数量超出上限")
    private List<String> sharedWithUsers;

    @Size(max = 50, message = "共享群组数量超出上限")
    private List<String> sharedWithGroups;

    private String visibility;
}
