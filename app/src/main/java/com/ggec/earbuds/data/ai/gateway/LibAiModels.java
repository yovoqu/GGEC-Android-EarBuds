package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.yovo.lib_ai.models.YoModelCapability;
import com.yovo.lib_ai.models.YoModelCatalog;
import com.yovo.lib_ai.models.YoModelDescriptor;
import com.yovo.lib_ai.models.YoModelFeature;
import com.yovo.lib_ai.models.YoModelInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台的模型列表混着向量、图片、语音模型，对话只要能聊天的那部分。
 * 排序：发布时间新的在前，没有发布时间的排在最后，再按名字。
 */
final class LibAiModels {

    private static final YoModelCatalog CATALOG = YoModelCatalog.standard();

    private LibAiModels() {
    }

    static List<AiModel> chatModels(List<YoModelInfo> infos) {
        List<YoModelInfo> chat = new ArrayList<>();
        for (YoModelInfo info : infos) {
            if (info.kind().chatCapable()) {
                chat.add(info);
            }
        }
        chat.sort(Comparator.comparingLong(LibAiModels::recency).reversed()
                .thenComparing(info -> info.displayName().toLowerCase(Locale.ROOT)));
        List<AiModel> models = new ArrayList<>();
        for (YoModelInfo info : chat) {
            models.add(toModel(info));
        }
        return models;
    }

    private static long recency(YoModelInfo info) {
        return info.createdAtMillis() > 0 ? info.createdAtMillis() : Long.MIN_VALUE;
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
                features, false);
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
