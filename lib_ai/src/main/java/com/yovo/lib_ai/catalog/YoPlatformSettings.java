package com.yovo.lib_ai.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 创建一个平台实例所需的全部输入。密钥只在这里短暂存在，{@code toString} 不输出它。
 */
public final class YoPlatformSettings {

    private final String templateId;
    private final String instanceId;
    private final String apiKey;
    @Nullable
    private final String baseUrl;

    private YoPlatformSettings(Builder builder) {
        this.templateId = builder.templateId;
        this.instanceId = builder.instanceId == null ? builder.templateId : builder.instanceId;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
    }

    @NonNull
    public static Builder builder(@NonNull String templateId) {
        return new Builder(templateId);
    }

    @NonNull
    public String templateId() {
        return templateId;
    }

    /**
     * 实例在客户端里的 id。内置平台默认等于模板标识。
     */
    @NonNull
    public String instanceId() {
        return instanceId;
    }

    @NonNull
    public String apiKey() {
        return apiKey;
    }

    /**
     * 为空表示使用模板的官方地址。
     */
    @Nullable
    public String baseUrl() {
        return baseUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoPlatformSettings{templateId=" + templateId
                + ", instanceId=" + instanceId
                + ", baseUrl=" + baseUrl + ", apiKey=redacted}";
    }

    public static final class Builder {
        private final String templateId;
        private String instanceId;
        private String apiKey;
        private String baseUrl;

        private Builder(String templateId) {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            this.templateId = templateId.trim();
        }

        @NonNull
        public Builder instanceId(@Nullable String instanceId) {
            this.instanceId = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        @NonNull
        public Builder baseUrl(@Nullable String baseUrl) {
            this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl;
            return this;
        }

        @NonNull
        public YoPlatformSettings build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey is required");
            }
            apiKey = apiKey.trim();
            return new YoPlatformSettings(this);
        }
    }
}
