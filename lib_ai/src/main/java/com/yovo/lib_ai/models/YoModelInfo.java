package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 平台模型列表里的一项。{@link #id()} 就是对话请求里的 {@code model} 字段。
 * 平台没返回的元数据保持为空，不凭 id 猜测数值。
 */
public final class YoModelInfo {

    private final String id;
    private final String displayName;
    @Nullable
    private final String ownedBy;
    private final long createdAtMillis;
    private final YoModelKind kind;
    @Nullable
    private final Integer contextWindow;
    @Nullable
    private final Integer maxOutputTokens;
    private final Set<YoModelFeature> features;
    private final String rawJson;

    private YoModelInfo(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName == null ? builder.id : builder.displayName;
        this.ownedBy = builder.ownedBy;
        this.createdAtMillis = builder.createdAtMillis;
        this.kind = builder.kind;
        this.contextWindow = builder.contextWindow;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.features = Collections.unmodifiableSet(EnumSet.copyOf(builder.features));
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

    /**
     * 展示名。平台没给时等于 {@link #id()}。
     */
    @NonNull
    public String displayName() {
        return displayName;
    }

    @Nullable
    public String ownedBy() {
        return ownedBy;
    }

    /**
     * 发布时间（毫秒）。平台没给或给了无效值时为 -1。
     */
    public long createdAtMillis() {
        return createdAtMillis;
    }

    @NonNull
    public YoModelKind kind() {
        return kind;
    }

    @Nullable
    public Integer contextWindow() {
        return contextWindow;
    }

    @Nullable
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    @NonNull
    public Set<YoModelFeature> features() {
        return features;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }

    public static final class Builder {
        private final String id;
        @Nullable
        private String displayName;
        @Nullable
        private String ownedBy;
        private long createdAtMillis = -1L;
        private YoModelKind kind = YoModelKind.CHAT;
        @Nullable
        private Integer contextWindow;
        @Nullable
        private Integer maxOutputTokens;
        private final EnumSet<YoModelFeature> features = EnumSet.noneOf(YoModelFeature.class);
        private String rawJson = "";

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("model id is required");
            }
            this.id = id.trim();
        }

        @NonNull
        public Builder displayName(@Nullable String displayName) {
            this.displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
            return this;
        }

        @NonNull
        public Builder ownedBy(@Nullable String ownedBy) {
            this.ownedBy = ownedBy == null || ownedBy.isBlank() ? null : ownedBy.trim();
            return this;
        }

        @NonNull
        public Builder createdAtMillis(long createdAtMillis) {
            this.createdAtMillis = createdAtMillis > 0 ? createdAtMillis : -1L;
            return this;
        }

        @NonNull
        public Builder kind(@NonNull YoModelKind kind) {
            this.kind = kind;
            return this;
        }

        @NonNull
        public Builder contextWindow(@Nullable Integer contextWindow) {
            this.contextWindow = contextWindow != null && contextWindow > 0 ? contextWindow : null;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens != null && maxOutputTokens > 0 ? maxOutputTokens : null;
            return this;
        }

        @NonNull
        public Builder feature(@NonNull YoModelFeature feature) {
            this.features.add(feature);
            return this;
        }

        @NonNull
        public Builder rawJson(@NonNull String rawJson) {
            this.rawJson = rawJson;
            return this;
        }

        @NonNull
        public YoModelInfo build() {
            return new YoModelInfo(this);
        }
    }
}
