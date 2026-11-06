package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量化结果：按请求顺序排列的向量，附用量与原始报文。
 */
public final class YoEmbeddingResult {

    private final List<YoEmbedding> embeddings;
    @Nullable
    private final String model;
    @Nullable
    private final Integer totalTokens;
    @Nullable
    private final Integer promptTokens;
    private final String rawJson;

    public YoEmbeddingResult(
            @NonNull List<YoEmbedding> embeddings,
            @Nullable String model,
            @Nullable Integer totalTokens,
            @Nullable Integer promptTokens,
            @NonNull String rawJson) {
        this.embeddings = Collections.unmodifiableList(new ArrayList<>(embeddings));
        this.model = model;
        this.totalTokens = totalTokens;
        this.promptTokens = promptTokens;
        this.rawJson = rawJson;
    }

    /** 向量列表，按 {@link YoEmbedding#index()} 升序。 */
    @NonNull
    public List<YoEmbedding> embeddings() {
        return embeddings;
    }

    /** 取第 {@code index} 条输入的向量；越界返回 {@code null}。 */
    @Nullable
    public List<Float> vector(int index) {
        for (YoEmbedding embedding : embeddings) {
            if (embedding.index() == index) {
                return embedding.vector();
            }
        }
        return null;
    }

    public int size() {
        return embeddings.size();
    }

    @Nullable
    public String model() {
        return model;
    }

    @Nullable
    public Integer totalTokens() {
        return totalTokens;
    }

    @Nullable
    public Integer promptTokens() {
        return promptTokens;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
