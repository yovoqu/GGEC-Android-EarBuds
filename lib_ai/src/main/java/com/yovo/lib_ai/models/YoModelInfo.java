package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoModelSpec;

import java.util.Objects;

/**
 * 平台模型列表里的一项。{@link #id()} 就是对话请求里的 {@code model} 字段。
 *
 * <p>平台说了什么都在 {@link #spec()} 里：解码时先按 id 推断出底，再把平台上报的类别、模态、
 * 限额、能力、推理逐项盖上去，所以 {@code spec()} 里<b>上报的</b>项是可信的，没上报的仍是推断或未知。
 * 平台没返回的元数据保持为空，不凭 id 猜数值。要叠加用户的覆盖，交给
 * {@link YoModelCatalog#resolve(YoModelInfo)}。</p>
 */
public final class YoModelInfo {

    private final YoModelSpec spec;
    @Nullable
    private final String ownedBy;
    private final long createdAtMillis;
    private final String rawJson;

    private YoModelInfo(Builder builder) {
        this.spec = builder.spec;
        this.ownedBy = builder.ownedBy;
        this.createdAtMillis = builder.createdAtMillis;
        this.rawJson = builder.rawJson;
    }

    @NonNull
    public static Builder builder(@NonNull YoModelSpec spec) {
        return new Builder(spec);
    }

    @NonNull
    public String id() {
        return spec.id();
    }

    /**
     * 展示名。平台没给时等于 {@link #id()}。
     */
    @NonNull
    public String displayName() {
        return spec.displayName();
    }

    /**
     * 推断加平台上报合成的规格，尚未叠加用户覆盖。
     */
    @NonNull
    public YoModelSpec spec() {
        return spec;
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
    public String rawJson() {
        return rawJson;
    }

    public static final class Builder {
        private final YoModelSpec spec;
        @Nullable
        private String ownedBy;
        private long createdAtMillis = -1L;
        private String rawJson = "";

        private Builder(YoModelSpec spec) {
            this.spec = Objects.requireNonNull(spec, "spec");
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
