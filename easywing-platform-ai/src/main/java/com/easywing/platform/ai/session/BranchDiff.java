package com.easywing.platform.ai.session;

import com.easywing.platform.ai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话分支差异比较结果
 * <p>
 * 按消息下标逐位对齐两个会话：下标同时存在且内容相同记为 {@code UNCHANGED}，内容不同记为 {@code EDITED}，
 * 仅源会话有记为 {@code REMOVED}，仅目标会话有记为 {@code ADDED}。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDiff {

    public static final String UNCHANGED = "UNCHANGED";
    public static final String EDITED = "EDITED";
    public static final String REMOVED = "REMOVED";
    public static final String ADDED = "ADDED";

    private String sourceSessionId;
    private String targetSessionId;
    private int sourceMessageCount;
    private int targetMessageCount;
    private int unchangedCount;
    private int editedCount;
    private int addedCount;
    private int removedCount;

    @Builder.Default
    private List<DiffEntry> entries = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffEntry {
        private int index;
        private String type;
        private ChatMessage source;
        private ChatMessage target;
    }
}
