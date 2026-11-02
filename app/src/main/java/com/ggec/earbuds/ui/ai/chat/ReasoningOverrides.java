package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 用户手动展开/折叠思考块的页面级覆盖状态。 */
final class ReasoningOverrides {

    private final Map<String, Boolean> values = new ConcurrentHashMap<>();

    void toggle(@NonNull String messageId, boolean expanded) {
        values.put(messageId, expanded);
    }

    @NonNull
    Map<String, Boolean> values() {
        return Map.copyOf(values);
    }
}
