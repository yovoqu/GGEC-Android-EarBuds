package com.yovo.lib_ai.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.List;

/**
 * 目录里一类平台的静态描述：怎么称呼、默认主机在哪、提供哪些协议。
 * 描述不含密钥，界面据此渲染平台列表和配置表单。
 */
public final class YoPlatformDescriptor {

    private final String templateId;
    private final String displayName;
    private final String defaultBaseUrl;
    private final List<YoProtocol> protocols;
    private final boolean custom;
    private final String apiKeyHint;
    @Nullable
    private final String consoleUrl;

    YoPlatformDescriptor(
            String templateId,
            String displayName,
            String defaultBaseUrl,
            List<YoProtocol> protocols,
            boolean custom,
            String apiKeyHint,
            @Nullable String consoleUrl) {
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("at least one protocol is required");
        }
        this.templateId = templateId;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.protocols = List.copyOf(protocols);
        this.custom = custom;
        this.apiKeyHint = apiKeyHint;
        this.consoleUrl = consoleUrl;
    }

    /**
     * 模板标识，也是内置平台的默认实例 id。
     */
    @NonNull
    public String templateId() {
        return templateId;
    }

    /**
     * 品牌名，不随界面语言变化。
     */
    @NonNull
    public String displayName() {
        return displayName;
    }

    /**
     * 官方主机地址。自定义模板没有默认值，返回空串。
     */
    @NonNull
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * 平台提供的协议，推荐的排在前面。
     */
    @NonNull
    public List<YoProtocol> protocols() {
        return protocols;
    }

    @NonNull
    public YoProtocol defaultProtocol() {
        return protocols.get(0);
    }

    /**
     * 自定义模板：主机地址由用户填写，可以创建多个实例。
     */
    public boolean custom() {
        return custom;
    }

    @NonNull
    public String apiKeyHint() {
        return apiKeyHint;
    }

    /**
     * 申请密钥的页面，没有可靠地址时为空。
     */
    @Nullable
    public String consoleUrl() {
        return consoleUrl;
    }
}
