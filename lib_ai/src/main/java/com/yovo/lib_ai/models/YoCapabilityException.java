package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiException;

import java.util.Collection;

/**
 * 路由失败：平台没有能提供这个能力的模型，或平台没声明对应的服务。
 *
 * <p>这类失败必须是**新**的错误类型，不能翻成 {@code BAD_REQUEST} 或 400——那两种提示
 * （"参数不对"/"模型不存在"）会把用户引到错误的排查方向。消息里固定带两件事：
 * 需要什么能力、该平台有哪些模型，界面直接照抄就能用。
 */
public final class YoCapabilityException extends YoAiException {

    private final YoAiErrorKind kind;
    private final YoModelCapability capability;

    private YoCapabilityException(@NonNull YoModelCapability capability,
                                  @NonNull YoAiErrorKind kind,
                                  @NonNull String message) {
        super(message, null);
        this.capability = capability;
        this.kind = kind;
    }

    @NonNull
    public YoAiErrorKind kind() {
        return kind;
    }

    /**
     * 想要的能力。
     */
    @NonNull
    public YoModelCapability capability() {
        return capability;
    }

    /**
     * 平台没声明任何能承载该能力的服务（例如要实时语音，但平台只有对话）。
     */
    @NonNull
    static YoCapabilityException serviceMissing(@NonNull YoModelCapability capability,
                                                @NonNull Collection<YoServiceKind> declaredServices,
                                                @NonNull Collection<String> platformModelIds) {
        return new YoCapabilityException(capability, YoAiErrorKind.UNSUPPORTED_CAPABILITY,
                "需要「" + capability.label() + "」能力（" + capability.name() + "），但平台没有声明对应的服务；"
                        + "已声明的服务：" + names(declaredServices)
                        + "；平台模型：" + ids(platformModelIds));
    }

    /**
     * 服务在，但平台列出的模型里没有具备该能力的。
     */
    @NonNull
    static YoCapabilityException modelMissing(@NonNull YoModelCapability capability,
                                              @NonNull Collection<YoServiceKind> declaredServices,
                                              @NonNull Collection<String> platformModelIds) {
        return new YoCapabilityException(capability, YoAiErrorKind.UNSUPPORTED_CAPABILITY,
                "需要「" + capability.label() + "」能力（" + capability.name() + "），"
                        + "但平台的模型里没有具备该能力的；已声明的服务：" + names(declaredServices)
                        + "；平台模型：" + ids(platformModelIds));
    }

    @NonNull
    private static String names(@NonNull Collection<YoServiceKind> services) {
        if (services.isEmpty()) {
            return "（无）";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (YoServiceKind service : services) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(service.name());
            first = false;
        }
        return builder.append(']').toString();
    }

    @NonNull
    private static String ids(@NonNull Collection<String> modelIds) {
        if (modelIds.isEmpty()) {
            return "（无）";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String id : modelIds) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(id);
            first = false;
        }
        return builder.append(']').toString();
    }
}
