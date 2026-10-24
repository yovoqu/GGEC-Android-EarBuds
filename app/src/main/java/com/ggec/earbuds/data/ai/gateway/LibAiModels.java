package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.yovo.lib_ai.models.YoModelCapability;
import com.yovo.lib_ai.models.YoModelCatalog;
import com.yovo.lib_ai.models.YoModelDescriptor;
import com.yovo.lib_ai.models.YoModelFeature;
import com.yovo.lib_ai.models.YoModelInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 平台的模型列表混着向量、图片、语音模型。拉取面板要全部列出并按类型分组（MOD-06），
 * 所以 {@link #models} 保留全部模型、保持接口返回顺序，只把 lib_ai 的类型映射过来。
 * {@link #chatModels} 是旧口径（只要对话模型、按发布时间排序），留给旧调用。
 */
final class LibAiModels {

    private static final YoModelCatalog CATALOG = YoModelCatalog.standard();

    private LibAiModels() {
    }

    /** 全部模型，接口返回顺序，带类型。同一 ID 只取第一次出现。 */
    static List<AiModel> models(List<YoModelInfo> infos) {
        List<AiModel> models = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (YoModelInfo info : infos) {
            if (seen.add(info.id())) {
                models.add(toModel(info));
            }
        }
        return models;
    }

    static AiModelKind kind(com.yovo.lib_ai.models.YoModelKind kind) {
        if (kind == null) {
            return AiModelKind.CHAT;
        }
        return switch (kind) {
            case CHAT -> AiModelKind.CHAT;
            case EMBEDDING -> AiModelKind.EMBEDDING;
            case IMAGE -> AiModelKind.IMAGE;
            case AUDIO -> AiModelKind.AUDIO;
            case MODERATION -> AiModelKind.MODERATION;
            case OTHER -> AiModelKind.OTHER;
        };
    }

    private static AiModel toModel(YoModelInfo info) {
        Set<AiModelFeature> features = providerFeatures(info);
        YoModelDescriptor known = CATALOG.describe(info.id());
        // 有明确出处（内置表 / 用户手写）的能力优先：那是按模型家族声明的，
        // 比"平台自己报的 feature 位"更可靠——同一份 id 在网关镜像上经常少报一位。
        // 只有推断结果时才回退到平台报的 feature，免得把平台知道的事也推断掉。
        if (known.source().trusted()) {
            features = declaredFeatures(known);
        }
        return new AiModel(info.id(), info.displayName(), info.contextWindow(), info.maxOutputTokens(),
                features, false, kind(info.kind()), false);
    }

    private static Set<AiModelFeature> providerFeatures(YoModelInfo info) {
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        for (YoModelFeature feature : info.features()) {
            features.add(feature == YoModelFeature.VISION ? AiModelFeature.VISION : AiModelFeature.REASONING);
        }
        return features;
    }

    private static Set<AiModelFeature> declaredFeatures(YoModelDescriptor descriptor) {
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        if (descriptor.supports(YoModelCapability.CHAT_VISION)) {
            features.add(AiModelFeature.VISION);
        }
        if (descriptor.supports(YoModelCapability.REASONING)) {
            features.add(AiModelFeature.REASONING);
        }
        return features;
    }
}
