package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一个批量任务。字段覆盖 OpenAI / Anthropic / Qwen 的并集，未提供的保持空。
 */
public final class YoBatchJob {

    private final String id;
    private final YoBatchStatus status;
    @Nullable
    private final String endpoint;
    @Nullable
    private final String inputFileId;
    @Nullable
    private final String outputFileId;
    @Nullable
    private final String errorFileId;
    @Nullable
    private final String resultsUrl;
    @Nullable
    private final String completionWindow;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final int totalRequests;
    private final int completedRequests;
    private final int failedRequests;
    private final List<String> errors;
    private final String rawJson;

    private YoBatchJob(Builder builder) {
        this.id = builder.id;
        this.status = builder.status;
        this.endpoint = builder.endpoint;
        this.inputFileId = builder.inputFileId;
        this.outputFileId = builder.outputFileId;
        this.errorFileId = builder.errorFileId;
        this.resultsUrl = builder.resultsUrl;
        this.completionWindow = builder.completionWindow;
        this.createdAtMillis = builder.createdAtMillis;
        this.expiresAtMillis = builder.expiresAtMillis;
        this.totalRequests = builder.totalRequests;
        this.completedRequests = builder.completedRequests;
        this.failedRequests = builder.failedRequests;
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.rawJson = builder.rawJson;
    }

    @NonNull
    public static Builder builder(@NonNull String id) {
        return new Builder(id);
    }

    @NonNull
    public String id() {
        return id;
    }

    @NonNull
    public YoBatchStatus status() {
        return status;
    }

    @Nullable
    public String endpoint() {
        return endpoint;
    }

    @Nullable
    public String inputFileId() {
        return inputFileId;
    }

    @Nullable
    public String outputFileId() {
        return outputFileId;
    }

    @Nullable
    public String errorFileId() {
        return errorFileId;
    }

    @Nullable
    public String resultsUrl() {
        return resultsUrl;
    }

    @Nullable
    public String completionWindow() {
        return completionWindow;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public int totalRequests() {
        return totalRequests;
    }

    public int completedRequests() {
        return completedRequests;
    }

    public int failedRequests() {
        return failedRequests;
    }

    /** 失败原因的人话列表。 */
    @NonNull
    public List<String> errors() {
        return errors;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    public static final class Builder {
        private final String id;
        private YoBatchStatus status = YoBatchStatus.UNKNOWN;
        @Nullable
        private String endpoint;
        @Nullable
        private String inputFileId;
        @Nullable
        private String outputFileId;
        @Nullable
        private String errorFileId;
        @Nullable
        private String resultsUrl;
        @Nullable
        private String completionWindow;
        private long createdAtMillis = -1L;
        private long expiresAtMillis = -1L;
        private int totalRequests;
        private int completedRequests;
        private int failedRequests;
        private final List<String> errors = new ArrayList<>();
        private String rawJson = "";

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("batch id is required");
            }
            this.id = id.trim();
        }

        @NonNull
        public Builder status(@NonNull YoBatchStatus status) {
            this.status = status;
            return this;
        }

        @NonNull
        public Builder endpoint(@Nullable String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        @NonNull
        public Builder inputFileId(@Nullable String inputFileId) {
            this.inputFileId = inputFileId;
            return this;
        }

        @NonNull
        public Builder outputFileId(@Nullable String outputFileId) {
            this.outputFileId = outputFileId;
            return this;
        }

        @NonNull
        public Builder errorFileId(@Nullable String errorFileId) {
            this.errorFileId = errorFileId;
            return this;
        }

        @NonNull
        public Builder resultsUrl(@Nullable String resultsUrl) {
            this.resultsUrl = resultsUrl;
            return this;
        }

        @NonNull
        public Builder completionWindow(@Nullable String completionWindow) {
            this.completionWindow = completionWindow;
            return this;
        }

        @NonNull
        public Builder createdAtMillis(long createdAtMillis) {
            this.createdAtMillis = createdAtMillis > 0 ? createdAtMillis : -1L;
            return this;
        }

        @NonNull
        public Builder expiresAtMillis(long expiresAtMillis) {
            this.expiresAtMillis = expiresAtMillis > 0 ? expiresAtMillis : -1L;
            return this;
        }

        @NonNull
        public Builder requestCounts(int total, int completed, int failed) {
            this.totalRequests = Math.max(0, total);
            this.completedRequests = Math.max(0, completed);
            this.failedRequests = Math.max(0, failed);
            return this;
        }

        @NonNull
        public Builder addError(@Nullable String error) {
            if (error != null && !error.isBlank()) {
                errors.add(error.trim());
            }
            return this;
        }

        @NonNull
        public Builder rawJson(@NonNull String rawJson) {
            this.rawJson = rawJson;
            return this;
        }

        @NonNull
        public YoBatchJob build() {
            return new YoBatchJob(this);
        }
    }
}
