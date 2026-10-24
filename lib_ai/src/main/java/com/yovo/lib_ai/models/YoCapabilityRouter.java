package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 能力 → 适配器的纯函数。输入"要什么能力、这台平台声明了哪些服务、它有哪些模型"，输出"用哪个模型、打哪个端点"。
 *
 * <p>刻意不接 {@code YoPlatform} 或客户端：路由要能被单测直接驱动，也不能因为一次平台改造就改签名。
 * 平台声明的那一组协议用 {@link YoServiceKind#fromProtocols(Collection)} 折过来即可。
 */
public interface YoCapabilityRouter {

    /**
     * @param capability        需要的能力
     * @param declaredServices  平台已声明支持的服务（对话三条 + 三条语音专用）
     * @param platformModelIds  平台给出的模型 id 列表，按平台的顺序（越靠前越优先）
     * @param preferredModelId  用户选定的模型；为 {@code null} 或它不支持该能力时按平台顺序挑
     * @throws YoAiException 没有能提供该能力的模型，{@link YoCapabilityException} 带能力与模型清单
     */
    @NonNull
    YoModelRoute route(@NonNull YoModelCapability capability,
                       @NonNull Set<YoServiceKind> declaredServices,
                       @NonNull List<String> platformModelIds,
                       @Nullable String preferredModelId) throws YoAiException;

    /**
     * 不要指定模型的重载。
     */
    @NonNull
    default YoModelRoute route(@NonNull YoModelCapability capability,
                               @NonNull Set<YoServiceKind> declaredServices,
                               @NonNull List<String> platformModelIds) throws YoAiException {
        return route(capability, declaredServices, platformModelIds, null);
    }

    /**
     * 平台直接给了 {@link YoProtocol} 集合时的重载（{@code YoCompletions#protocols()} 是这种形状）。
     */
    @NonNull
    default YoModelRoute routeWithProtocols(@NonNull YoModelCapability capability,
                                            @NonNull Collection<YoProtocol> declaredProtocols,
                                            @NonNull List<String> platformModelIds) throws YoAiException {
        return route(capability, YoServiceKind.fromProtocols(declaredProtocols), platformModelIds, null);
    }
}
