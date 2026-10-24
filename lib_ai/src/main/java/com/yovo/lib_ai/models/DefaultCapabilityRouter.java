package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 默认路由：先按能力定服务，再在平台给出的模型里挑一个真的具备该能力的。
 *
 * <p>两条挑选原则：
 * <ul>
 *   <li>**出处优先**：{@code DECLARED}/{@code USER} 的模型先于 {@code INFERRED}。推断出来的"大概能看图"
 *       只在内置表没命中时才用——这正是 §3.3 那条"看起来支持、实际 400"的防线。</li>
 *   <li>**平台顺序**：同一档里按平台列表顺序取第一个，平台通常把主推模型排前面。</li>
 * </ul>
 *
 * <p>用户显式指定了模型时优先用它（哪怕它不在本次列表里——手动添加的模型就属于这种），
 * 只有在它确实不具备该能力时才回落到列表。
 */
public final class DefaultCapabilityRouter implements YoCapabilityRouter {

    /**
     * 对话能力的协议偏好：先 Chat Completions（各平台支持最广），再 Responses，最后 Messages。
     */
    private static final YoServiceKind[] CHAT_PREFERENCE = {
            YoServiceKind.CHAT_COMPLETIONS,
            YoServiceKind.RESPONSES,
            YoServiceKind.MESSAGES
    };

    private final String baseUrl;
    private final YoModelCatalog catalog;

    public DefaultCapabilityRouter(@NonNull String baseUrl) {
        this(baseUrl, YoModelCatalog.standard());
    }

    public DefaultCapabilityRouter(@NonNull String baseUrl, @NonNull YoModelCatalog catalog) {
        this.baseUrl = YoServiceTarget.normalizeBaseUrl(baseUrl);
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * 归一化后的基址（去了尾部斜杠）。
     */
    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    @NonNull
    public YoModelCatalog catalog() {
        return catalog;
    }

    @NonNull
    @Override
    public YoModelRoute route(@NonNull YoModelCapability capability,
                              @NonNull Set<YoServiceKind> declaredServices,
                              @NonNull List<String> platformModelIds,
                              @Nullable String preferredModelId) throws YoAiException {
        Objects.requireNonNull(capability, "capability");
        Set<YoServiceKind> services = declaredServices == null ? Set.of() : declaredServices;
        List<String> modelIds = platformModelIds == null ? List.of() : platformModelIds;

        YoServiceKind serviceKind = serviceFor(capability, services, modelIds);
        YoModelDescriptor model = pick(capability, services, modelIds, preferredModelId);
        String endpoint = YoServiceEndpoints.endpoint(baseUrl, serviceKind, model.id());
        return new YoModelRoute(capability, model, serviceKind, endpoint);
    }

    /**
     * 能力 → 服务。对话能力取平台声明的三条里偏好最高的那条；语音能力只有一种载体。
     */
    @NonNull
    private static YoServiceKind serviceFor(@NonNull YoModelCapability capability,
                                           @NonNull Set<YoServiceKind> declaredServices,
                                           @NonNull List<String> modelIds) {
        if (capability == YoModelCapability.EMBEDDING) {
            // 本轮只留枚举位：没有嵌入客户端，提前说清楚比走到一半 404 好。
            throw YoCapabilityException.serviceMissing(capability, declaredServices, modelIds);
        }
        if (capability.conversational()) {
            for (YoServiceKind candidate : CHAT_PREFERENCE) {
                if (declaredServices.contains(candidate)) {
                    return candidate;
                }
            }
            throw YoCapabilityException.serviceMissing(capability, declaredServices, modelIds);
        }
        YoServiceKind required = capability.serviceKind();
        if (!declaredServices.contains(required)) {
            throw YoCapabilityException.serviceMissing(capability, declaredServices, modelIds);
        }
        return required;
    }

    @NonNull
    private YoModelDescriptor pick(@NonNull YoModelCapability capability,
                                   @NonNull Set<YoServiceKind> declaredServices,
                                   @NonNull List<String> modelIds,
                                   @Nullable String preferredModelId) {
        if (preferredModelId != null && !preferredModelId.isBlank()) {
            YoModelDescriptor preferred = catalog.describe(preferredModelId);
            if (preferred.supports(capability)) {
                return preferred;
            }
        }
        YoModelDescriptor inferredMatch = null;
        for (String id : modelIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            YoModelDescriptor descriptor = catalog.describe(id);
            if (!descriptor.supports(capability)) {
                continue;
            }
            if (descriptor.source().trusted()) {
                return descriptor;
            }
            if (inferredMatch == null) {
                inferredMatch = descriptor;
            }
        }
        if (inferredMatch != null) {
            return inferredMatch;
        }
        throw YoCapabilityException.modelMissing(capability, declaredServices, modelIds);
    }
}
