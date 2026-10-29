package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.platform.YoServiceTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 默认路由：先按类别定服务，再在平台给出的模型里挑一个真的满足要求的。
 *
 * <p>两条挑选原则：
 * <ul>
 *   <li>**出处优先**：{@link YoModelFit#CONFIRMED}（每条依据都来自平台上报或用户）的模型先于
 *       {@link YoModelFit#INFERRED}。推断出来的"大概能用"只在没有确认过的模型时才用——
 *       这正是那条"看起来支持、实际 400"的防线。</li>
 *   <li>**平台顺序**：同一档里按平台列表顺序取第一个，平台通常把主推模型排前面。</li>
 * </ul>
 *
 * <p>用户显式指定了模型时优先用它（哪怕它不在本次列表里——手动添加的模型就属于这种），
 * 只有在它确实不满足要求时才回落到列表。</p>
 *
 * <p>{@link #route} 收到的规格被认为已经解析好（建议用 {@link YoModelCatalog#resolveAll} 得到，
 * 带着平台上报和用户覆盖）；{@link #routeByIds} 只有 id，由本类持有的目录推断并叠加覆盖。</p>
 */
public final class DefaultCapabilityRouter implements YoCapabilityRouter {

    /**
     * 对话类的协议偏好：先 Chat Completions（各平台支持最广），再 Responses，最后 Messages。
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
    public YoModelRoute route(@NonNull YoModelRequirement requirement,
                              @NonNull Set<YoServiceKind> declaredServices,
                              @NonNull List<YoModelSpec> models,
                              @Nullable String preferredModelId) throws YoAiException {
        Objects.requireNonNull(requirement, "requirement");
        Set<YoServiceKind> services = declaredServices == null ? Set.of() : declaredServices;
        List<YoModelSpec> candidates = models == null ? List.of() : models;

        YoServiceKind serviceKind = serviceFor(requirement, services, candidates);
        Selection selection = pick(requirement, services, candidates, preferredModelId);
        String endpoint = YoServiceEndpoints.endpoint(baseUrl, serviceKind, selection.spec.id());
        return new YoModelRoute(requirement, selection.spec, selection.fit, serviceKind, endpoint);
    }

    @NonNull
    @Override
    public YoModelRoute routeByIds(@NonNull YoModelRequirement requirement,
                                   @NonNull Set<YoServiceKind> declaredServices,
                                   @NonNull List<String> modelIds,
                                   @Nullable String preferredModelId) throws YoAiException {
        return route(requirement, declaredServices,
                catalog.describeAll(modelIds == null ? List.of() : modelIds), preferredModelId);
    }

    /**
     * 类别 → 服务。对话类取平台声明的三条里偏好最高的那条；其余类别各有一种专用服务，
     * 必须是平台声明过的；SDK 还没有服务的类别提前说清楚，比走到一半 404 好。
     */
    @NonNull
    private static YoServiceKind serviceFor(@NonNull YoModelRequirement requirement,
                                           @NonNull Set<YoServiceKind> declaredServices,
                                           @NonNull List<YoModelSpec> models) {
        YoModelCategory category = requirement.category();
        if (category.conversational()) {
            for (YoServiceKind candidate : CHAT_PREFERENCE) {
                if (declaredServices.contains(candidate)) {
                    return candidate;
                }
            }
            throw YoCapabilityException.serviceMissing(requirement, declaredServices, ids(models));
        }
        YoServiceKind dedicated = YoServiceKind.dedicatedFor(category);
        if (dedicated == null) {
            throw YoCapabilityException.serviceUnavailable(requirement, declaredServices, ids(models));
        }
        if (!declaredServices.contains(dedicated)) {
            throw YoCapabilityException.serviceMissing(requirement, declaredServices, ids(models));
        }
        return dedicated;
    }

    @NonNull
    private Selection pick(@NonNull YoModelRequirement requirement,
                           @NonNull Set<YoServiceKind> declaredServices,
                           @NonNull List<YoModelSpec> models,
                           @Nullable String preferredModelId) {
        if (preferredModelId != null && !preferredModelId.isBlank()) {
            YoModelSpec preferred = find(models, preferredModelId.trim());
            if (preferred == null) {
                preferred = catalog.describe(preferredModelId);
            }
            YoModelFit fit = requirement.fit(preferred);
            if (fit.accepted()) {
                return new Selection(preferred, fit);
            }
        }
        Selection inferredMatch = null;
        for (YoModelSpec spec : models) {
            YoModelFit fit = requirement.fit(spec);
            if (fit == YoModelFit.CONFIRMED) {
                return new Selection(spec, fit);
            }
            if (fit == YoModelFit.INFERRED && inferredMatch == null) {
                inferredMatch = new Selection(spec, fit);
            }
        }
        if (inferredMatch != null) {
            return inferredMatch;
        }
        throw YoCapabilityException.modelMissing(requirement, declaredServices, ids(models));
    }

    @Nullable
    private static YoModelSpec find(@NonNull List<YoModelSpec> models, @NonNull String id) {
        for (YoModelSpec spec : models) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        for (YoModelSpec spec : models) {
            if (spec.id().equalsIgnoreCase(id)) {
                return spec;
            }
        }
        return null;
    }

    @NonNull
    private static List<String> ids(@NonNull List<YoModelSpec> models) {
        List<String> ids = new ArrayList<>(models.size());
        for (YoModelSpec spec : models) {
            ids.add(spec.id());
        }
        return ids;
    }

    private record Selection(YoModelSpec spec, YoModelFit fit) {
    }
}
