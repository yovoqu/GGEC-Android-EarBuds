package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 重排结果：按相关性分数降序排列的条目，附用量与原始报文。
 */
public final class YoRerankResult {

    private final List<YoRerankItem> results;
    @Nullable
    private final Integer promptTokens;
    @Nullable
    private final Integer totalTokens;
    private final String rawJson;

    public YoRerankResult(
            @NonNull List<YoRerankItem> results,
            @Nullable Integer promptTokens,
            @Nullable Integer totalTokens,
            @NonNull String rawJson) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.promptTokens = promptTokens;
        this.totalTokens = totalTokens;
        this.rawJson = rawJson;
    }

    @NonNull
    public List<YoRerankItem> results() {
        return results;
    }

    @Nullable
    public Integer promptTokens() {
        return promptTokens;
    }

    @Nullable
    public Integer totalTokens() {
        return totalTokens;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
