package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.models.spec.YoModelSpec;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 要求 → 适配器的纯函数。输入"要什么样的模型、这台平台声明了哪些服务、它有哪些模型"，
 * 输出"用哪个模型、打哪个端点"。
 *
 * <p>刻意不接 {@code YoPlatform} 或客户端：路由要能被单测直接驱动，也不能因为一次平台改造就改签名。
 * 平台声明的那一组协议用 {@link YoServiceKind#fromProtocols(Collection)} 折过来即可。</p>
 *
 * <p>模型有两种给法：已经解析好的规格（拉过平台列表，带着平台上报的类别、模态、限额），
 * 或者只有 id（用户手填、上次存下来的）。前者更准，后者回落到按 id 推断。</p>
 */
public interface YoCapabilityRouter {

    /**
     * @param requirement      对模型的要求
     * @param declaredServices 平台已声明支持的服务（对话三条 + 专用服务）
     * @param models           平台的模型规格，按平台的顺序（越靠前越优先）
     * @param preferredModelId 用户选定的模型；为 {@code null} 或它不满足要求时按平台顺序挑
     * @throws YoAiException 没有能满足要求的模型，{@link YoCapabilityException} 带要求与模型清单
     */
    @NonNull
    YoModelRoute route(@NonNull YoModelRequirement requirement,
                       @NonNull Set<YoServiceKind> declaredServices,
                       @NonNull List<YoModelSpec> models,
                       @Nullable String preferredModelId) throws YoAiException;

    /**
     * 模型只有 id 的版本：规格按 id 推断，再叠加路由持有的用户覆盖。
     */
    @NonNull
    YoModelRoute routeByIds(@NonNull YoModelRequirement requirement,
                            @NonNull Set<YoServiceKind> declaredServices,
                            @NonNull List<String> modelIds,
                            @Nullable String preferredModelId) throws YoAiException;

    /**
     * 不要指定模型的重载。
     */
    @NonNull
    default YoModelRoute route(@NonNull YoModelRequirement requirement,
                               @NonNull Set<YoServiceKind> declaredServices,
                               @NonNull List<YoModelSpec> models) throws YoAiException {
        return route(requirement, declaredServices, models, null);
    }

    /**
     * 平台直接给了 {@link YoProtocol} 集合时的重载（{@code YoCompletions#protocols()} 是这种形状）。
     */
    @NonNull
    default YoModelRoute routeWithProtocols(@NonNull YoModelRequirement requirement,
                                            @NonNull Collection<YoProtocol> declaredProtocols,
                                            @NonNull List<YoModelSpec> models) throws YoAiException {
        return route(requirement, YoServiceKind.fromProtocols(declaredProtocols), models, null);
    }
}
