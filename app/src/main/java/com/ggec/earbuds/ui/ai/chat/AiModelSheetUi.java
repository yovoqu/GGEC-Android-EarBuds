package com.ggec.earbuds.ui.ai.chat;

import java.util.List;

/**
 * 模型选择面板：按已配置的平台分组，每组是该平台能用的模型。
 */
public record AiModelSheetUi(List<Group> groups) {

    public record Group(String platformId, String platformName, List<Row> rows) {
        public Group {
            rows = List.copyOf(rows);
        }
    }

    /**
     * @param firstTokenMillis 最近一次测速的首字延迟，没有测过或测失败为 -1
     */
    public record Row(String modelId, String name, int contextWindow, long firstTokenMillis, boolean selected) {
    }

    public AiModelSheetUi {
        groups = List.copyOf(groups);
    }

    public boolean empty() {
        for (Group group : groups) {
            if (!group.rows().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
