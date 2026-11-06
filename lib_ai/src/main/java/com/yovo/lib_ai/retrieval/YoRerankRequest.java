package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次重排请求。Qwen DashScope {@code text-rerank} 的语义形状。
 *
 * <p>{@code query} 与每条 {@code document} 既可以是纯文本，也可以是 {@code {"text"|"image"|"video": ...}}
 * 的结构化内容（多模态重排）；这里用 {@code Object} 承载，编码时按类型写出。</p>
 */
public final class YoRerankRequest {

    private final String model;
    private final String query;
    private final List<Object> documents;
    @Nullable
    private final Integer topN;
    @Nullable
    private final Boolean returnDocuments;
    @Nullable
    private final String instruct;
    @Nullable
    private final Double fps;

    private YoRerankRequest(Builder builder) {
        this.model = builder.model;
        this.query = builder.query;
        this.documents = Collections.unmodifiableList(new ArrayList<>(builder.documents));
        this.topN = builder.topN;
        this.returnDocuments = builder.returnDocuments;
        this.instruct = builder.instruct;
        this.fps = builder.fps;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String model() {
        return model;
    }

    @NonNull
    public String query() {
        return query;
    }

    /** 候选文档，元素为 {@link String} 或 {@link JSONObject}。 */
    @NonNull
    public List<Object> documents() {
        return documents;
    }

    @Nullable
    public Integer topN() {
        return topN;
    }

    @Nullable
    public Boolean returnDocuments() {
        return returnDocuments;
    }

    @Nullable
    public String instruct() {
        return instruct;
    }

    @Nullable
    public Double fps() {
        return fps;
    }

    public static final class Builder {

        private String model;
        private String query;
        private final List<Object> documents = new ArrayList<>();
        @Nullable
        private Integer topN;
        @Nullable
        private Boolean returnDocuments;
        @Nullable
        private String instruct;
        @Nullable
        private Double fps;

        private Builder() {
        }

        @NonNull
        public Builder model(@Nullable String model) {
            this.model = model == null ? null : model.trim();
            return this;
        }

        @NonNull
        public Builder query(@Nullable String query) {
            this.query = query == null ? null : query.trim();
            return this;
        }

        @NonNull
        public Builder addDocument(@Nullable String text) {
            if (text != null) {
                documents.add(text);
            }
            return this;
        }

        @NonNull
        public Builder addDocument(@Nullable JSONObject structured) {
            if (structured != null) {
                documents.add(structured);
            }
            return this;
        }

        @NonNull
        public Builder documents(@Nullable List<String> texts) {
            if (texts != null) {
                for (String text : texts) {
                    if (text != null) {
                        documents.add(text);
                    }
                }
            }
            return this;
        }

        @NonNull
        public Builder topN(@Nullable Integer topN) {
            if (topN != null && topN < 1) {
                throw new IllegalArgumentException("topN must be positive");
            }
            this.topN = topN;
            return this;
        }

        @NonNull
        public Builder returnDocuments(@Nullable Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
            return this;
        }

        @NonNull
        public Builder instruct(@Nullable String instruct) {
            this.instruct = instruct == null || instruct.isBlank() ? null : instruct.trim();
            return this;
        }

        @NonNull
        public Builder fps(@Nullable Double fps) {
            if (fps != null && (fps < 0 || fps > 1)) {
                throw new IllegalArgumentException("fps must be within [0, 1]");
            }
            this.fps = fps;
            return this;
        }

        @NonNull
        public YoRerankRequest build() {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query is required");
            }
            if (documents.isEmpty()) {
                throw new IllegalArgumentException("at least one document is required");
            }
            return new YoRerankRequest(this);
        }
    }
}
