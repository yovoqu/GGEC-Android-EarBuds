package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一条候选的排序结果。
 *
 * <p>{@link #score()} 只在同一次请求内可比；{@link #document()} 仅在请求要求
 * {@code return_documents} 时由服务端回填，否则为空。</p>
 */
public final class YoRerankItem {

    private final int index;
    private final double score;
    @Nullable
    private final String document;

    public YoRerankItem(int index, double score, @Nullable String document) {
        this.index = index;
        this.score = score;
        this.document = document;
    }

    public int index() {
        return index;
    }

    public double score() {
        return score;
    }

    @Nullable
    public String document() {
        return document;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoRerankItem{index=" + index + ", score=" + score + '}';
    }
}
