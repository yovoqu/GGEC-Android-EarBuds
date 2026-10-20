package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 模型列表的一页。{@link #nextCursor()} 为空表示已经是最后一页。
 */
public final class YoModelPage {

    private final List<YoModelInfo> models;
    @Nullable
    private final String nextCursor;

    public YoModelPage(@NonNull List<YoModelInfo> models, @Nullable String nextCursor) {
        this.models = List.copyOf(models);
        this.nextCursor = nextCursor == null || nextCursor.isBlank() ? null : nextCursor;
    }

    @NonNull
    public List<YoModelInfo> models() {
        return models;
    }

    @Nullable
    public String nextCursor() {
        return nextCursor;
    }
}
