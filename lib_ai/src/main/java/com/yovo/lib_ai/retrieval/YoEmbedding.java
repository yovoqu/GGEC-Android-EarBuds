package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一个向量：批次里的序号与浮点分量。
 *
 * <p>{@link #index()} 对应请求里第几条输入（OpenAI 用 {@code data[].index}，
 * DashScope 用 {@code output.embeddings[].text_index}），解码时已归一到 0 基。</p>
 */
public final class YoEmbedding {

    private final int index;
    private final List<Float> vector;

    public YoEmbedding(int index, @NonNull List<Float> vector) {
        this.index = index;
        this.vector = Collections.unmodifiableList(new ArrayList<>(vector));
    }

    public int index() {
        return index;
    }

    /** 向量分量。 */
    @NonNull
    public List<Float> vector() {
        return vector;
    }

    public int dimensions() {
        return vector.size();
    }

    @NonNull
    @Override
    public String toString() {
        return "YoEmbedding{index=" + index + ", dimensions=" + vector.size() + '}';
    }
}
