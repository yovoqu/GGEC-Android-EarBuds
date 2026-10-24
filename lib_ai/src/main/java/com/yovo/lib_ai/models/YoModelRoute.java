package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.Objects;

/**
 * 一次路由的结论：用哪个模型、落到哪个服务、打哪个地址。
 *
 * @param capability  调用方要的能力
 * @param model       选中的模型（带能力和出处）
 * @param serviceKind 落到的服务
 * @param protocol    对话协议；三条专用服务（转写/合成/实时）为 {@code null}
 * @param endpoint    完整地址（实时语音是 {@code wss://…}）
 */
public record YoModelRoute(
        YoModelCapability capability,
        YoModelDescriptor model,
        YoServiceKind serviceKind,
        @Nullable YoProtocol protocol,
        String endpoint) {

    public YoModelRoute {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(serviceKind, "serviceKind");
        protocol = protocol != null ? protocol : serviceKind.asProtocol();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        endpoint = endpoint.trim();
    }

    /**
     * 不带协议字段的构造：对话服务自动带上 {@link YoServiceKind#asProtocol()}。
     */
    public YoModelRoute(@NonNull YoModelCapability capability,
                        @NonNull YoModelDescriptor model,
                        @NonNull YoServiceKind serviceKind,
                        @NonNull String endpoint) {
        this(capability, model, serviceKind, serviceKind.asProtocol(), endpoint);
    }

    /**
     * 走对话协议。
     */
    public boolean conversational() {
        return serviceKind.conversational();
    }
}
