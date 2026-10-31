package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.models.spec.YoModelSpec;

import java.util.Objects;

/**
 * 一次路由的结论：用哪个模型、落到哪个服务、打哪个地址，以及这个选择有多可靠。
 *
 * @param requirement 调用方的要求
 * @param model       选中的模型（带完整规格和各项出处）
 * @param fit         选中的模型满足要求的可靠程度；{@link YoModelFit#INFERRED} 时界面应提示"待确认"
 * @param serviceKind 落到的服务
 * @param protocol    对话协议；专用服务（转写、合成、向量、实时）为 {@code null}
 * @param endpoint    完整地址（实时语音是 {@code wss://…}）
 */
public record YoModelRoute(
        YoModelRequirement requirement,
        YoModelSpec model,
        YoModelFit fit,
        YoServiceKind serviceKind,
        @Nullable YoProtocol protocol,
        String endpoint) {

    public YoModelRoute {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(serviceKind, "serviceKind");
        if (!fit.accepted()) {
            throw new IllegalArgumentException("a route needs a model that fits the requirement");
        }
        protocol = protocol != null ? protocol : serviceKind.asProtocol();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        endpoint = endpoint.trim();
    }

    /**
     * 不带协议字段的构造：对话服务自动带上 {@link YoServiceKind#asProtocol()}。
     */
    public YoModelRoute(@NonNull YoModelRequirement requirement,
                        @NonNull YoModelSpec model,
                        @NonNull YoModelFit fit,
                        @NonNull YoServiceKind serviceKind,
                        @NonNull String endpoint) {
        this(requirement, model, fit, serviceKind, serviceKind.asProtocol(), endpoint);
    }

    /**
     * 走对话协议。
     */
    public boolean conversational() {
        return serviceKind.conversational();
    }

    /**
     * 每一条依据都来自平台上报或用户指定，可以放心断言。
     */
    public boolean confirmed() {
        return fit == YoModelFit.CONFIRMED;
    }
}
