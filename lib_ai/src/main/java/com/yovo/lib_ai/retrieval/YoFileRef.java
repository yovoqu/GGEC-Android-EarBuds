package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 平台上一个已存在的文件。不同厂商字段名不同，统一到这里的稳定形状。
 *
 * <p>{@link #id()} 是后续引用（聊天里的 {@code file_id}、Responses 的 {@code file} 输入项、
 * Batch 的 {@code input_file_id}）用的键。{@link #rawJson()} 保留原报文便于排障。</p>
 */
public final class YoFileRef {

    private final String id;
    @Nullable
    private final String filename;
    private final long bytes;
    @Nullable
    private final String purpose;
    @Nullable
    private final String status;
    private final long createdAtMillis;
    private final String rawJson;

    private YoFileRef(Builder builder) {
        this.id = builder.id;
        this.filename = builder.filename;
        this.bytes = builder.bytes;
        this.purpose = builder.purpose;
        this.status = builder.status;
        this.createdAtMillis = builder.createdAtMillis;
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

    @Nullable
    public String filename() {
        return filename;
    }

    public long bytes() {
        return bytes;
    }

    @Nullable
    public String purpose() {
        return purpose;
    }

    @Nullable
    public String status() {
        return status;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    public static final class Builder {
        private final String id;
        @Nullable
        private String filename;
        private long bytes = -1L;
        @Nullable
        private String purpose;
        @Nullable
        private String status;
        private long createdAtMillis = -1L;
        private String rawJson = "";

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("file id is required");
            }
            this.id = id.trim();
        }

        @NonNull
        public Builder filename(@Nullable String filename) {
            this.filename = filename == null || filename.isBlank() ? null : filename.trim();
            return this;
        }

        @NonNull
        public Builder bytes(long bytes) {
            this.bytes = bytes >= 0 ? bytes : -1L;
            return this;
        }

        @NonNull
        public Builder purpose(@Nullable String purpose) {
            this.purpose = purpose == null || purpose.isBlank() ? null : purpose.trim();
            return this;
        }

        @NonNull
        public Builder status(@Nullable String status) {
            this.status = status == null || status.isBlank() ? null : status.trim();
            return this;
        }

        @NonNull
        public Builder createdAtMillis(long createdAtMillis) {
            this.createdAtMillis = createdAtMillis > 0 ? createdAtMillis : -1L;
            return this;
        }

        @NonNull
        public Builder rawJson(@NonNull String rawJson) {
            this.rawJson = rawJson;
            return this;
        }

        @NonNull
        public YoFileRef build() {
            return new YoFileRef(this);
        }
    }
}
